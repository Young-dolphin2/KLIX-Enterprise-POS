-- 1. Create Customers Table
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    address TEXT,
    id_type TEXT, -- 'National ID', 'Passport', 'Driving License'
    id_number TEXT,
    profile_image_url TEXT,
    membership_status TEXT DEFAULT 'ACTIVE', -- 'ACTIVE', 'EXPIRED', 'SUSPENDED'
    membership_expiry TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid()
);

-- 2. Create Rental/Asset History Table (Phase 2 foundation)
CREATE TABLE asset_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID REFERENCES customers(id) ON DELETE CASCADE,
    inventory_id UUID REFERENCES inventory(id) ON DELETE CASCADE,
    action_type TEXT NOT NULL, -- 'CHECK_OUT', 'CHECK_IN', 'BOOKING'
    action_timestamp TIMESTAMPTZ DEFAULT now(),
    notes TEXT,
    fuel_level TEXT,
    mileage INTEGER,
    damage_report TEXT,
    performed_by TEXT -- Employee name
);

-- 3. Enable RLS
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE asset_history ENABLE ROW LEVEL SECURITY;

-- 4. Policies
CREATE POLICY "Users can manage their own customers" ON customers
    FOR ALL USING (auth.uid() = owner_id);

CREATE POLICY "Users can manage their own asset history" ON asset_history
    FOR ALL USING (EXISTS (
        SELECT 1 FROM customers WHERE customers.id = asset_history.customer_id AND customers.owner_id = auth.uid()
    ));
