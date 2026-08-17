ALTER TABLE facilities
    ADD COLUMN building_id UUID;

ALTER TABLE facilities
    ADD CONSTRAINT fk_facilities_building
        FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE CASCADE;

-- Legacy facilities remain unassigned and therefore invisible. All new rows
-- are assigned by the application to the authenticated manager's building.
CREATE INDEX idx_facilities_building_id
    ON facilities(building_id)
    WHERE building_id IS NOT NULL;
