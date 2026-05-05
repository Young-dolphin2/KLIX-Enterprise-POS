-- Inject tenant tracking
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();
ALTER TABLE sales ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();
ALTER TABLE employees ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();
ALTER TABLE categories ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();
ALTER TABLE branches ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT auth.uid();

-- Create Indexes for Hyper Speed
CREATE INDEX IF NOT EXISTS idx_menu_tenant ON menu_items(tenant_id);
CREATE INDEX IF NOT EXISTS idx_inventory_tenant ON inventory(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sales_tenant ON sales(tenant_id);
CREATE INDEX IF NOT EXISTS idx_employees_tenant ON employees(tenant_id);

-- Enable RLS
ALTER TABLE menu_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE branches ENABLE ROW LEVEL SECURITY;

-- Apply Universal Policies
CREATE POLICY "Tenant Isolation Policy" ON menu_items FOR ALL USING (tenant_id = auth.uid());
CREATE POLICY "Tenant Isolation Policy" ON inventory FOR ALL USING (tenant_id = auth.uid());
CREATE POLICY "Tenant Isolation Policy" ON sales FOR ALL USING (tenant_id = auth.uid());
CREATE POLICY "Tenant Isolation Policy" ON sale_items FOR ALL USING (tenant_id = auth.uid());
CREATE POLICY "Tenant Isolation Policy" ON employees FOR ALL USING (tenant_id = auth.uid());
CREATE POLICY "Tenant Isolation Policy" ON categories FOR ALL USING (tenant_id = auth.uid());
CREATE POLICY "Tenant Isolation Policy" ON branches FOR ALL USING (tenant_id = auth.uid());
