CREATE TABLE staff_building_memberships (
    staff_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_staff_building_memberships_building
    ON staff_building_memberships(building_id);

-- Preserve the most recently accepted building for existing staff. Older
-- releases recorded accepted staff invitations but had no membership table.
INSERT INTO staff_building_memberships (staff_id, building_id, joined_at)
SELECT DISTINCT ON (invitation.accepted_by)
    invitation.accepted_by,
    invitation.building_id,
    COALESCE(invitation.accepted_at, invitation.created_at)
FROM building_invitations invitation
JOIN users staff ON staff.id = invitation.accepted_by
WHERE invitation.status = 'ACCEPTED'
  AND invitation.role = 'STAFF'
  AND invitation.accepted_by IS NOT NULL
  AND staff.role = 'STAFF'
ORDER BY invitation.accepted_by, invitation.accepted_at DESC NULLS LAST, invitation.created_at DESC;
