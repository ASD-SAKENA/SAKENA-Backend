CREATE TABLE IF NOT EXISTS building_invitations (
    id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    -- The secret the invitee presents; unrelated to the id, so knowing an
    -- invitation exists is not enough to accept it.
    token VARCHAR(64) NOT NULL UNIQUE,
    channel VARCHAR(10) NOT NULL,
    recipient VARCHAR(200),
    role VARCHAR(20) NOT NULL,
    apartment_id UUID REFERENCES apartments(id) ON DELETE SET NULL,
    tenancy VARCHAR(20),
    invited_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    accepted_by UUID,
    accepted_at TIMESTAMP
);

CREATE INDEX idx_building_invitations_building
    ON building_invitations(building_id, created_at DESC);
