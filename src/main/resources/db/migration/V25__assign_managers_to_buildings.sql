ALTER TABLE buildings
    ADD COLUMN manager_id UUID;

ALTER TABLE buildings
    ADD CONSTRAINT fk_buildings_manager
        FOREIGN KEY (manager_id) REFERENCES users(id) ON DELETE RESTRICT;

-- A manager owns at most one building. The partial index lets deployments
-- retain pre-existing, unassigned buildings while all newly-created buildings
-- are assigned by the application.
CREATE UNIQUE INDEX uq_buildings_manager_id
    ON buildings(manager_id)
    WHERE manager_id IS NOT NULL;
