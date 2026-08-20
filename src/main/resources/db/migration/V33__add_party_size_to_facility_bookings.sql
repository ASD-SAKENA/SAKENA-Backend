-- A facility's capacity is described to residents as "N نفر", but bookings
-- were counted one row each, so a 20-person pool filled after 20 bookings
-- regardless of how many people actually came. A booking now carries the
-- number of people it brings, and capacity is summed over that.
ALTER TABLE facility_bookings
    ADD COLUMN party_size INTEGER NOT NULL DEFAULT 1
        CONSTRAINT ck_facility_bookings_party_size CHECK (party_size >= 1);

-- Existing bookings predate the column; one person each preserves exactly the
-- occupancy they had under the old row-counting rule.
ALTER TABLE facility_bookings ALTER COLUMN party_size DROP DEFAULT;
