-- Create expenses table
CREATE TABLE IF NOT EXISTS public.expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount DECIMAL(12, 2) NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    branch_id UUID REFERENCES public.branches(id),
    "timestamp" TIMESTAMPTZ DEFAULT now(),
    tenant_id UUID DEFAULT auth.uid()
);

-- Enable RLS
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation
CREATE POLICY "Employees can manage expenses for their tenant"
ON public.expenses
FOR ALL
USING (tenant_id = auth.uid())
WITH CHECK (tenant_id = auth.uid());

-- Index for performance
CREATE INDEX idx_expenses_branch_id ON public.expenses(branch_id);
CREATE INDEX idx_expenses_category ON public.expenses(category);
