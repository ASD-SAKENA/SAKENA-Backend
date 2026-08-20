-- Staff were a system-wide pool: every manager could see and assign every
-- staff account in the deployment. A staff member now belongs to the
-- buildings they were invited into, the same way a resident belongs to one.
CREATE TABLE staff_building_memberships (
    id UUID PRIMARY KEY,
    staff_id UUID NOT NULL REFERENCES users(id),
    building_id UUID NOT NULL REFERENCES buildings(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_staff_building UNIQUE (staff_id, building_id)
);

CREATE INDEX idx_staff_building_memberships_building
    ON staff_building_memberships(building_id);

-- Existing staff predate this table and would otherwise vanish from every
-- manager's picker. Grant each of them membership of every current building,
-- preserving today's behaviour for data already in flight; new staff only
-- reach a building through its own invitation.
INSERT INTO staff_building_memberships (id, staff_id, building_id, created_at)
SELECT gen_random_uuid(), u.id, b.id, NOW()
FROM users u
CROSS JOIN buildings b
WHERE u.role = 'STAFF';
