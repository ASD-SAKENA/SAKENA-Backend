-- Booking price was computed on the fly for display only and never charged.
-- It is now taken from the resident's wallet at booking time, so the amount
-- actually paid is stored on the row: a later change to the facility's
-- hourly rate must not alter what an existing booking cost, and a refund
-- has to return exactly what was taken.
ALTER TABLE facility_bookings
    ADD COLUMN price NUMERIC(14, 2) NOT NULL DEFAULT 0
        CONSTRAINT ck_facility_bookings_price CHECK (price >= 0);

ALTER TABLE facility_bookings ALTER COLUMN price DROP DEFAULT;

-- Cancelling used to delete the row, which erased the occupancy history and
-- left no trace of a refund. A cancelled booking is kept and excluded from
-- capacity instead.
ALTER TABLE facility_bookings
    ADD COLUMN cancelled_at TIMESTAMPTZ;

-- Every capacity and listing query filters on this, so index it alongside
-- the facility the query already narrows by.
CREATE INDEX idx_facility_bookings_active
    ON facility_bookings(facility_id, starts_at)
    WHERE cancelled_at IS NULL;
