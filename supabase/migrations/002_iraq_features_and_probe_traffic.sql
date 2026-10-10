-- ============================================================================
-- Migration 002: Iraq Specific Road Reports, Anonymous Traffic Probes, & Saved Places
-- ============================================================================

-- 1. Extend road_reports type check constraint with Iraqi road conditions:
--    'checkpoint' (سيطرة / نقطة تفتيش)
--    'speed_bump' (مطب صناعي / طسة)
--    'pothole'    (حفرة / تخسف طريق)
ALTER TABLE public.road_reports DROP CONSTRAINT IF EXISTS road_reports_type_check;

ALTER TABLE public.road_reports ADD CONSTRAINT road_reports_type_check
    CHECK (type IN (
        'accident', 'traffic', 'closure', 'roadwork',
        'hazard', 'breakdown', 'flood', 'camera',
        'checkpoint', 'speed_bump', 'pothole'
    ));

-- 2. Anonymous Live Traffic Probes (Crowdsourced Speed & Heading)
--    Speed and location samples sent anonymously (NO user_id) to calculate congestion.
--    Short TTL of 30 minutes, after which records automatically expire.
CREATE TABLE IF NOT EXISTS public.traffic_probes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed_kmh DOUBLE PRECISION NOT NULL,
    bearing DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 minutes')
);

CREATE INDEX IF NOT EXISTS idx_traffic_probes_lat_lng ON public.traffic_probes (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_traffic_probes_expires ON public.traffic_probes (expires_at);

ALTER TABLE public.traffic_probes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can insert anonymous traffic probes"
    ON public.traffic_probes FOR INSERT
    WITH CHECK (
        latitude BETWEEN -90 AND 90 AND
        longitude BETWEEN -180 AND 180 AND
        speed_kmh >= 0 AND
        speed_kmh <= 250 AND
        expires_at > NOW()
    );

CREATE POLICY "Active traffic probes are readable by all users"
    ON public.traffic_probes FOR SELECT
    USING (expires_at > NOW());

-- Automatic cleanup function for expired traffic probes and expired reports
CREATE OR REPLACE FUNCTION public.cleanup_expired_laze_data()
RETURNS VOID AS $$
BEGIN
    DELETE FROM public.traffic_probes WHERE expires_at < NOW();
    UPDATE public.road_reports SET status = 'expired' WHERE status = 'active' AND expires_at < NOW();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Synchronized User Saved Places (Home, Work, Favorites)
CREATE TABLE IF NOT EXISTS public.saved_places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('home', 'work', 'favorite')),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saved_places_user ON public.saved_places (user_id);

ALTER TABLE public.saved_places ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own saved places"
    ON public.saved_places FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);
