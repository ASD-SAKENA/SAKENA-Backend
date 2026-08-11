CREATE TABLE IF NOT EXISTS charge_periods (
    id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    type VARCHAR(20) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_charge_periods_building_starts_on
    ON charge_periods(building_id, starts_on DESC);

CREATE TABLE IF NOT EXISTS charge_items (
    id UUID PRIMARY KEY,
    period_id UUID NOT NULL REFERENCES charge_periods(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    allocation VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_charge_items_period ON charge_items(period_id);

CREATE TABLE IF NOT EXISTS unit_invoices (
    id UUID PRIMARY KEY,
    period_id UUID NOT NULL REFERENCES charge_periods(id) ON DELETE CASCADE,
    apartment_id UUID NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    amount NUMERIC(18, 2) NOT NULL,
    paid_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    issued_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_unit_invoices_period_apartment UNIQUE (period_id, apartment_id)
);

CREATE INDEX idx_unit_invoices_apartment ON unit_invoices(apartment_id, issued_at DESC);
