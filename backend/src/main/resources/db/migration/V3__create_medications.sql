-- initial_stock is the immutable opening input, not current inventory.
-- Phase 5 will migrate known opening amounts into the append-only inventory ledger.
CREATE TABLE medications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    initial_stock NUMERIC(19,6),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT medication_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT medication_unit_not_blank CHECK (length(trim(unit)) > 0),
    CONSTRAINT medication_initial_stock_nonnegative CHECK (initial_stock IS NULL OR initial_stock >= 0)
);
CREATE INDEX medications_owner_created ON medications(user_id, created_at DESC, id DESC);
