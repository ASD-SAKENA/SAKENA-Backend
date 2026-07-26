-- Booking policy per facility: opening hours, slot length, advance window,
-- weekly quota per resident and the hourly price. Defaults mirror
-- BookingRules.DEFAULT so existing rows stay bookable exactly as before.
ALTER TABLE facilities
    ADD COLUMN opens_at TIME NOT NULL DEFAULT '08:00',
    ADD COLUMN closes_at TIME NOT NULL DEFAULT '22:00',
    ADD COLUMN closed_days VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN min_duration_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN max_duration_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN max_advance_days INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN max_per_resident_per_week INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN hourly_price NUMERIC(18, 2) NOT NULL DEFAULT 0;

-- Serving "my upcoming bookings" and the weekly-quota count.
CREATE INDEX IF NOT EXISTS idx_facility_bookings_booked_by_starts_at
    ON facility_bookings (booked_by, starts_at);
