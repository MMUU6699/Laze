-- ============================================================================
-- Laze Navigation: Remote App Configuration (Supabase Cloud Config)
-- Migration: 004_app_remote_config.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.app_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    is_public BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Enable Row Level Security
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;

-- Allow anon and authenticated users to read public configurations
DROP POLICY IF EXISTS "Public read access to public config" ON public.app_config;
CREATE POLICY "Public read access to public config"
    ON public.app_config
    FOR SELECT
    USING (is_public = true);

-- Helper RPC function to query a config value directly
CREATE OR REPLACE FUNCTION public.get_app_config(config_key TEXT)
RETURNS TEXT AS $$
DECLARE
    cfg_value TEXT;
BEGIN
    SELECT value INTO cfg_value
    FROM public.app_config
    WHERE key = config_key AND is_public = true;

    RETURN cfg_value;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grants
GRANT SELECT ON public.app_config TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_app_config(TEXT) TO anon, authenticated;

-- Insert / Update Mapbox Access Token
INSERT INTO public.app_config (key, value, description, is_public, updated_at)
VALUES (
    'mapbox_token',
    'YOUR_MAPBOX_ACCESS_TOKEN',
    'Mapbox API Access Token for directions, search and satellite layers',
    true,
    now()
)
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value,
    updated_at = now();
