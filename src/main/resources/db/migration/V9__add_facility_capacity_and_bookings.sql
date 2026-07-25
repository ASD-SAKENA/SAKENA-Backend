ALTER TABLE facilities
    ADD COLUMN capacity INTEGER NOT NULL DEFAULT 10;

CREATE TABLE IF NOT EXISTS facility_bookings (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
    booked_by UUID NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_facility_bookings_facility_time
    ON facility_bookings(facility_id, starts_at, ends_at);
