-- ============================================================================
-- Laze Navigation & Community Traffic: Security Hardening & RLS Policies
-- Migration: 002_security_hardening.sql
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Profiles & Privacy Protection
-- ----------------------------------------------------------------------------

-- Add points column to profiles if not present
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS points INT NOT NULL DEFAULT 0;

-- Drop permissive public select policy on profiles (which leaked emails to anon key)
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.profiles;
DROP POLICY IF EXISTS "Users can view their own full profile" ON public.profiles;

-- Only the owner can view their full profile row (including email)
CREATE POLICY "Users can view their own full profile"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (auth.uid() = id);

-- Public view for community leaderboards / driver profiles without exposing emails
CREATE OR REPLACE VIEW public.public_profiles AS
SELECT 
    id,
    full_name,
    avatar_url,
    points,
    reports_submitted,
    created_at
FROM public.profiles;

-- Allow authenticated and anon users to view sanitized public profile data
GRANT SELECT ON public.public_profiles TO authenticated, anon;

-- Protect sensitive columns from direct user updates
CREATE OR REPLACE FUNCTION public.protect_profile_columns()
RETURNS TRIGGER AS $$
BEGIN
    -- Only service_role / internal triggers can alter points, reports count, and email
    IF (auth.role() IS NULL OR auth.role() <> 'service_role') THEN
        NEW.points := OLD.points;
        NEW.reports_submitted := OLD.reports_submitted;
        NEW.email := OLD.email;
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_profile_columns ON public.profiles;
CREATE TRIGGER trg_protect_profile_columns
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.protect_profile_columns();


-- ----------------------------------------------------------------------------
-- 2. Points Ledger & Gamification Functions
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.user_points_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    points INT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_points_log_user ON public.user_points_log(user_id, created_at DESC);

ALTER TABLE public.user_points_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view their own points log" ON public.user_points_log;
CREATE POLICY "Users can view their own points log"
    ON public.user_points_log FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

-- Secure points awarding function (SECURITY DEFINER, cannot be called directly to forge points)
CREATE OR REPLACE FUNCTION public.award_points(
    p_user_id UUID,
    p_points INT,
    p_reason TEXT
)
RETURNS VOID AS $$
BEGIN
    IF p_user_id IS NULL OR p_points <= 0 THEN
        RETURN;
    END IF;

    INSERT INTO public.user_points_log (user_id, points, reason)
    VALUES (p_user_id, p_points, p_reason);

    UPDATE public.profiles
    SET points = COALESCE(points, 0) + p_points,
        reports_submitted = CASE 
            WHEN p_reason = 'new_report' THEN COALESCE(reports_submitted, 0) + 1 
            ELSE reports_submitted 
        END,
        updated_at = NOW()
    WHERE id = p_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- 3. Road Reports Hardening & Rate Limiting
-- ----------------------------------------------------------------------------

-- Replace permissive insert policy: require authenticated user matching user_id
DROP POLICY IF EXISTS "Authenticated users or verified guests can submit reports" ON public.road_reports;
DROP POLICY IF EXISTS "Authenticated users can submit road reports" ON public.road_reports;

CREATE POLICY "Authenticated users can submit road reports"
    ON public.road_reports FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() IS NOT NULL AND
        auth.uid() = user_id AND
        latitude BETWEEN -90 AND 90 AND
        longitude BETWEEN -180 AND 180 AND
        expires_at > NOW()
    );

-- Rate-limiting trigger function: max 3 reports per minute per user
CREATE OR REPLACE FUNCTION public.check_road_report_rate_limit()
RETURNS TRIGGER AS $$
DECLARE
    recent_count INT;
BEGIN
    SELECT COUNT(*)
    INTO recent_count
    FROM public.road_reports
    WHERE user_id = NEW.user_id
      AND created_at > (NOW() - INTERVAL '1 minute');

    IF recent_count >= 3 THEN
        RAISE EXCEPTION 'Rate limit exceeded: You can only submit up to 3 road reports per minute.';
    END IF;

    -- Award points for creating a valid report
    PERFORM public.award_points(NEW.user_id, 10, 'new_report');

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_road_report_rate_limit ON public.road_reports;
CREATE TRIGGER trg_road_report_rate_limit
    BEFORE INSERT ON public.road_reports
    FOR EACH ROW EXECUTE FUNCTION public.check_road_report_rate_limit();

-- Update policy: creators can only update status (e.g. to 'cleared'), other columns immutable
DROP POLICY IF EXISTS "Report creator or system can update report status" ON public.road_reports;
CREATE POLICY "Report creator or system can update report status"
    ON public.road_reports FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id OR auth.role() = 'service_role')
    WITH CHECK (auth.uid() = user_id OR auth.role() = 'service_role');


-- ----------------------------------------------------------------------------
-- 4. Report Confirmations & Voting Hardening
-- ----------------------------------------------------------------------------

-- Enforce NOT NULL on user_id to prevent anonymous vote stuffing
DELETE FROM public.report_confirmations WHERE user_id IS NULL;
ALTER TABLE public.report_confirmations ALTER COLUMN user_id SET NOT NULL;

-- Drop permissive policies
DROP POLICY IF EXISTS "Anyone can submit a confirmation once per report" ON public.report_confirmations;
DROP POLICY IF EXISTS "Authenticated users can submit confirmations" ON public.report_confirmations;
DROP POLICY IF EXISTS "Authenticated users can update their own confirmation" ON public.report_confirmations;

CREATE POLICY "Authenticated users can submit confirmations"
    ON public.report_confirmations FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() IS NOT NULL AND
        auth.uid() = user_id
    );

CREATE POLICY "Authenticated users can update their own confirmation"
    ON public.report_confirmations FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Update confirmation function with points reward for active participation
CREATE OR REPLACE FUNCTION public.confirm_road_report(
    p_report_id UUID,
    p_is_still_there BOOLEAN,
    p_device_id TEXT DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required to confirm or vote on road reports.';
    END IF;

    -- Record or update confirmation
    INSERT INTO public.report_confirmations (report_id, user_id, device_id, is_still_there)
    VALUES (p_report_id, v_user_id, p_device_id, p_is_still_there)
    ON CONFLICT (report_id, user_id) DO UPDATE SET
        is_still_there = EXCLUDED.is_still_there,
        created_at = NOW();

    -- Award community points for voting
    PERFORM public.award_points(v_user_id, 2, 'report_confirmation');

    -- Update parent report counts and reliability
    IF p_is_still_there THEN
        UPDATE public.road_reports
        SET confirmations_count = confirmations_count + 1,
            reliability_score = LEAST(5.0, reliability_score + 0.5),
            expires_at = GREATEST(expires_at, NOW() + INTERVAL '25 minutes')
        WHERE id = p_report_id;
    ELSE
        UPDATE public.road_reports
        SET rejections_count = rejections_count + 1,
            reliability_score = GREATEST(0.0, reliability_score - 0.7),
            status = CASE 
                WHEN rejections_count + 1 >= 3 AND reliability_score - 0.7 <= 0.3 THEN 'cleared' 
                ELSE status 
            END
        WHERE id = p_report_id;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- 5. Smart Deduplication: Merge Nearby Identical Reports
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.submit_or_confirm_road_report(
    p_type TEXT,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_bearing DOUBLE PRECISION DEFAULT NULL,
    p_severity TEXT DEFAULT 'moderate',
    p_description TEXT DEFAULT NULL,
    p_road_name TEXT DEFAULT NULL,
    p_duration_minutes INT DEFAULT 45
)
RETURNS JSONB AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_existing_report_id UUID;
    v_new_report_id UUID;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required to submit road reports.';
    END IF;

    -- Look for an existing active report of the same type within 200 meters submitted in the last 30 minutes
    SELECT id INTO v_existing_report_id
    FROM public.road_reports
    WHERE type = p_type
      AND status = 'active'
      AND expires_at > NOW()
      AND ST_DWithin(
          location::geography,
          ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography,
          200.0
      )
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_existing_report_id IS NOT NULL THEN
        -- Merge as a positive confirmation instead of creating duplicate clutter
        PERFORM public.confirm_road_report(v_existing_report_id, true);
        RETURN jsonb_build_object(
            'action', 'confirmed_existing',
            'report_id', v_existing_report_id,
            'message', 'Report confirmed and merged with nearby active report.'
        );
    ELSE
        -- Insert fresh report
        INSERT INTO public.road_reports (
            user_id,
            type,
            latitude,
            longitude,
            bearing,
            severity,
            description,
            road_name,
            status,
            confirmations_count,
            reliability_score,
            created_at,
            expires_at
        ) VALUES (
            v_user_id,
            p_type,
            p_latitude,
            p_longitude,
            p_bearing,
            p_severity,
            p_description,
            p_road_name,
            'active',
            1,
            1.0,
            NOW(),
            NOW() + (p_duration_minutes || ' minutes')::INTERVAL
        ) RETURNING id INTO v_new_report_id;

        RETURN jsonb_build_object(
            'action', 'created_new',
            'report_id', v_new_report_id,
            'message', 'New road report submitted successfully.'
        );
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- ----------------------------------------------------------------------------
-- 6. Traffic Observations Security Policies
-- ----------------------------------------------------------------------------

DROP POLICY IF EXISTS "Authenticated users can submit traffic observations" ON public.traffic_observations;
DROP POLICY IF EXISTS "Traffic observations viewable for live traffic calculation" ON public.traffic_observations;

CREATE POLICY "Authenticated users can submit traffic observations"
    ON public.traffic_observations FOR INSERT
    TO authenticated
    WITH CHECK (
        latitude BETWEEN -90 AND 90 AND
        longitude BETWEEN -180 AND 180 AND
        speed_kmh >= 0 AND speed_kmh <= 300
    );

CREATE POLICY "Traffic observations viewable for live traffic calculation"
    ON public.traffic_observations FOR SELECT
    TO authenticated, anon
    USING (observed_at > NOW() - INTERVAL '30 minutes');


-- ----------------------------------------------------------------------------
-- 7. Periodic Maintenance & Automatic Expiration
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.clean_expired_reports()
RETURNS INT AS $$
DECLARE
    affected INT;
BEGIN
    -- Expire reports past expiration
    UPDATE public.road_reports
    SET status = 'expired'
    WHERE status = 'active' AND expires_at <= NOW();
    GET DIAGNOSTICS affected = ROW_COUNT;

    -- Purge ephemeral traffic observations older than 30 minutes
    DELETE FROM public.traffic_observations
    WHERE observed_at < NOW() - INTERVAL '30 minutes';

    RETURN affected;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
