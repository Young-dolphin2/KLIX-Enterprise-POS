-- ============================================================
-- KLIX POS - Subscriptions & Payment Transactions Tables
-- Applied via Supabase Management API
-- ============================================================

-- 1. SUBSCRIPTIONS TABLE
CREATE TABLE IF NOT EXISTS public.subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT auth.uid(),
    plan TEXT NOT NULL CHECK (plan IN ('free', 'pro', 'enterprise')),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'expired', 'cancelled')),
    current_period_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '1 month'),
    trial_end TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. PAYMENT TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS public.payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID REFERENCES public.subscriptions(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL DEFAULT auth.uid(),
    gateway TEXT NOT NULL CHECK (gateway IN ('paychangu', 'flutterwave', 'stripe')),
    tx_ref TEXT NOT NULL UNIQUE,
    transaction_id TEXT,
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL DEFAULT 'MWK',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'success', 'failed', 'refunded')),
    gateway_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. SYSTEM LOGS TABLE
CREATE TABLE IF NOT EXISTS public.system_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    level TEXT NOT NULL CHECK (level IN ('INFO', 'WARN', 'ERROR', 'FATAL')),
    tag TEXT NOT NULL,
    message TEXT NOT NULL,
    stack_trace TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    machine_name TEXT,
    app_version TEXT DEFAULT '1.0.0',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- INDEXES
CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant ON public.subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON public.subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_period_end ON public.subscriptions(current_period_end);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_tenant ON public.payment_transactions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_tx_ref ON public.payment_transactions(tx_ref);
CREATE INDEX IF NOT EXISTS idx_system_logs_tag ON public.system_logs(tag);
CREATE INDEX IF NOT EXISTS idx_system_logs_level ON public.system_logs(level);
CREATE INDEX IF NOT EXISTS idx_system_logs_created ON public.system_logs(created_at DESC);

-- ROW LEVEL SECURITY
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Tenant can view own subscription" ON public.subscriptions FOR SELECT USING (tenant_id = auth.uid());
CREATE POLICY "Service role can manage all subscriptions" ON public.subscriptions FOR ALL USING (auth.role() = 'service_role');

ALTER TABLE public.payment_transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Tenant can view own payments" ON public.payment_transactions FOR SELECT USING (tenant_id = auth.uid());
CREATE POLICY "Service role can manage all payments" ON public.payment_transactions FOR ALL USING (auth.role() = 'service_role');

ALTER TABLE public.system_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can insert logs" ON public.system_logs FOR INSERT WITH CHECK (true);
CREATE POLICY "Service role can view all logs" ON public.system_logs FOR SELECT USING (auth.role() = 'service_role');

-- TRIGGERS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER LANGUAGE plpgsql
AS 'BEGIN NEW.updated_at = now(); RETURN NEW; END;';

CREATE TRIGGER trigger_subscriptions_updated_at
    BEFORE UPDATE ON public.subscriptions
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER trigger_payment_transactions_updated_at
    BEFORE UPDATE ON public.payment_transactions
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- SUBSCRIPTION EXPIRY FUNCTION
CREATE OR REPLACE FUNCTION public.expire_subscriptions()
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER
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
