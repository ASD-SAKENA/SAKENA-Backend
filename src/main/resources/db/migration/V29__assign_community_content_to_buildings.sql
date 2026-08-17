ALTER TABLE announcements
    ADD COLUMN building_id UUID;

ALTER TABLE announcements
    ADD CONSTRAINT fk_announcements_building
        FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE CASCADE;

CREATE INDEX idx_announcements_building_created_at
    ON announcements(building_id, created_at DESC);

ALTER TABLE polls
    ADD COLUMN building_id UUID;

ALTER TABLE polls
    ADD CONSTRAINT fk_polls_building
        FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE CASCADE;

CREATE INDEX idx_polls_building_created_at
    ON polls(building_id, created_at DESC);
