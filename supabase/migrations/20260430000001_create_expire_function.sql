CREATE OR REPLACE FUNCTION public.expire_subscriptions()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    expired_count integer;
BEGIN
    UPDATE public.subscriptions
    SET status = 'expired'
    WHERE status = 'active'
      AND current_period_end < now()
      AND current_period_end > (now() - interval '3 days');

    GET DIAGNOSTICS expired_count = ROW_COUNT;
    RETURN expired_count;
END;
$$;
