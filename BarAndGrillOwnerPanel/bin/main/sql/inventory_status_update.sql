-- Update Inventory Table to support Asset Status
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'AVAILABLE'; -- 'AVAILABLE', 'RENTED', 'MAINTENANCE'

-- Ensure status is synced in RLS (if applicable, but existing policies should cover it)
