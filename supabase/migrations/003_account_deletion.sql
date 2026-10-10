-- ============================================================================
-- Laze Navigation: Account Deletion (Google Play Store Compliance)
-- Migration: 003_account_deletion.sql
-- ============================================================================

-- Function to allow an authenticated user to permanently delete their own account and associated personal data
CREATE OR REPLACE FUNCTION public.delete_own_account()
RETURNS VOID AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Clean up points ledger for this user
    DELETE FROM public.user_points_log WHERE user_id = v_user_id;

    -- Clean up confirmations submitted by this user
    DELETE FROM public.report_confirmations WHERE user_id = v_user_id;

    -- Detach user_id from road reports (keep community reports active without personal info)
    UPDATE public.road_reports SET user_id = NULL WHERE user_id = v_user_id;

    -- Delete public profile
    DELETE FROM public.profiles WHERE id = v_user_id;

    -- Delete user from auth schema
    DELETE FROM auth.users WHERE id = v_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.delete_own_account() TO authenticated;
