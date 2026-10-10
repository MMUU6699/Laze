-- ============================================================================
-- Laze Navigation & Community Traffic Schema
-- Database Migrations for Supabase (PostgreSQL with PostGIS)
-- ============================================================================

-- Enable PostGIS extension if available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Profiles Table (linked to Supabase Auth users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    reports_submitted INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- RLS for profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public profiles are viewable by everyone"
    ON public.profiles FOR SELECT
    USING (true);

CREATE POLICY "Users can insert their own profile"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update their own profile"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id);

-- Trigger to auto-create profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name, avatar_url)
    VALUES (
        new.id,
        new.email,
        COALESCE(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name', split_part(new.email, '@', 1)),
        new.raw_user_meta_data->>'avatar_url'
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        full_name = COALESCE(EXCLUDED.full_name, public.profiles.full_name),
        avatar_url = COALESCE(EXCLUDED.avatar_url, public.profiles.avatar_url),
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 2. Road Reports Table
CREATE TABLE IF NOT EXISTS public.road_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    type TEXT NOT NULL CHECK (type IN (
        'accident', 'traffic', 'closure', 'roadwork',
        'hazard', 'breakdown', 'flood', 'camera'
    )),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    bearing DOUBLE PRECISION,
    severity TEXT NOT NULL DEFAULT 'moderate' CHECK (severity IN ('low', 'moderate', 'high', 'severe')),
    description TEXT,
    road_name TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cleared', 'expired')),
    confirmations_count INT NOT NULL DEFAULT 1,
    rejections_count INT NOT NULL DEFAULT 0,
    reliability_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    -- PostGIS geometry point (WGS84, 4326)
    location GEOMETRY(Point, 4326) GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
    ) STORED
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_road_reports_location ON public.road_reports USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_road_reports_lat_lng ON public.road_reports (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_road_reports_status_expires ON public.road_reports (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_road_reports_created ON public.road_reports (created_at DESC);

-- RLS for road_reports
ALTER TABLE public.road_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Active road reports are viewable by all users"
    ON public.road_reports FOR SELECT
    USING (status = 'active' AND expires_at > NOW());

CREATE POLICY "Authenticated users or verified guests can submit reports"
    ON public.road_reports FOR INSERT
    WITH CHECK (
        latitude BETWEEN -90 AND 90 AND
        longitude BETWEEN -180 AND 180 AND
        expires_at > NOW()
    );

CREATE POLICY "Report creator or system can update report status"
    ON public.road_reports FOR UPDATE
    USING (auth.uid() = user_id OR auth.role() = 'service_role');

-- 3. Report Confirmations Table (Upvoting / Downvoting by other drivers)
CREATE TABLE IF NOT EXISTS public.report_confirmations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES public.road_reports(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    device_id TEXT, -- Optional device fingerprint for guest voters
    is_still_there BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_report_confirmation UNIQUE (report_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_confirmations_report ON public.report_confirmations(report_id);

-- RLS for confirmations
ALTER TABLE public.report_confirmations ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Confirmations viewable by all"
    ON public.report_confirmations FOR SELECT
    USING (true);

CREATE POLICY "Anyone can submit a confirmation once per report"
    ON public.report_confirmations FOR INSERT
    WITH CHECK (true);

-- 4. Traffic Observations Table (Aggregated crowdsourced speed, optional & privacy-safe)
CREATE TABLE IF NOT EXISTS public.traffic_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed_kmh DOUBLE PRECISION NOT NULL,
    heading_deg DOUBLE PRECISION,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Delete observations older than 30 minutes to preserve privacy
CREATE INDEX IF NOT EXISTS idx_traffic_observations_time ON public.traffic_observations (observed_at);
ALTER TABLE public.traffic_observations ENABLE ROW LEVEL SECURITY;

-- 5. Stored Procedures / RPC Functions for Spatial Queries

-- Function to query active reports within a given radius in meters
CREATE OR REPLACE FUNCTION public.get_nearby_road_reports(
    center_lat DOUBLE PRECISION,
    center_lng DOUBLE PRECISION,
    radius_meters DOUBLE PRECISION DEFAULT 15000
)
RETURNS SETOF public.road_reports AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM public.road_reports
    WHERE status = 'active'
      AND expires_at > NOW()
      AND ST_DWithin(
          location::geography,
          ST_SetSRID(ST_MakePoint(center_lng, center_lat), 4326)::geography,
          radius_meters
      )
    ORDER BY created_at DESC;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- Function to confirm or reject a report, recalculating reliability score
CREATE OR REPLACE FUNCTION public.confirm_road_report(
    p_report_id UUID,
    p_is_still_there BOOLEAN,
    p_device_id TEXT DEFAULT NULL
)
RETURNS VOID AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    -- Record confirmation
    INSERT INTO public.report_confirmations (report_id, user_id, device_id, is_still_there)
    VALUES (p_report_id, v_user_id, p_device_id, p_is_still_there)
    ON CONFLICT (report_id, user_id) DO UPDATE SET
        is_still_there = EXCLUDED.is_still_there,
        created_at = NOW();

    -- Update parent report counts and reliability
    IF p_is_still_there THEN
        UPDATE public.road_reports
        SET confirmations_count = confirmations_count + 1,
            reliability_score = LEAST(5.0, reliability_score + 0.5),
            expires_at = GREATEST(expires_at, NOW() + INTERVAL '20 minutes')
        WHERE id = p_report_id;
    ELSE
        UPDATE public.road_reports
        SET rejections_count = rejections_count + 1,
            reliability_score = GREATEST(0.0, reliability_score - 0.7),
            status = CASE WHEN rejections_count + 1 >= 3 AND reliability_score - 0.7 <= 0.3 THEN 'cleared' ELSE status END
        WHERE id = p_report_id;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Auto-cleanup function for expired reports
CREATE OR REPLACE FUNCTION public.clean_expired_reports()
RETURNS INT AS $$
DECLARE
    affected INT;
BEGIN
    UPDATE public.road_reports
    SET status = 'expired'
    WHERE status = 'active' AND expires_at <= NOW();
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
