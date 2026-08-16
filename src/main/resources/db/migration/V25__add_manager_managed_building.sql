ALTER TABLE users
    ADD COLUMN managed_building_id UUID NULL REFERENCES buildings (id);

CREATE INDEX idx_users_managed_building_id ON users (managed_building_id);
