CREATE TABLE IF NOT EXISTS residencies (
    id UUID PRIMARY KEY,
    apartment_id UUID NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    resident_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenancy VARCHAR(20) NOT NULL,
    moved_in_at TIMESTAMP NOT NULL,
    -- NULL means the residency is current; ended ones are kept as history.
    moved_out_at TIMESTAMP
);

-- A unit has at most one current resident, and a resident at most one unit.
CREATE UNIQUE INDEX uq_residencies_current_apartment
    ON residencies(apartment_id) WHERE moved_out_at IS NULL;
CREATE UNIQUE INDEX uq_residencies_current_resident
    ON residencies(resident_id) WHERE moved_out_at IS NULL;

CREATE INDEX idx_residencies_apartment ON residencies(apartment_id, moved_in_at DESC);
