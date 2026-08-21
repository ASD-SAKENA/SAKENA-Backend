-- Service staff are a separate pool of accounts assigned work across
-- buildings; they are never members of a building or occupants of a unit.
-- The guards enforcing that were added after the fact, so any staff who
-- slipped in earlier is removed here.

-- Vacate any unit a staff account still occupies. Residencies are history,
-- so the row is ended rather than deleted: the unit's past occupancy stays
-- readable, it just no longer counts as current.
UPDATE residencies r
SET moved_out_at = NOW()
FROM users u
WHERE r.resident_id = u.id
  AND u.role = 'STAFF'
  AND r.moved_out_at IS NULL;

-- A staff account that accepted a resident invitation was granted building
-- membership it should never have had. Revoking the acceptance drops them
-- from the members list without touching invitations legitimately issued to
-- them as staff.
UPDATE building_invitations i
SET status = 'REVOKED',
    accepted_by = NULL,
    accepted_at = NULL
FROM users u
WHERE i.accepted_by = u.id
  AND u.role = 'STAFF'
  AND i.role <> 'STAFF';

-- A unit was never valid on a staff invitation; clear any left from before
-- that was enforced, so re-accepting one can never start a residency.
UPDATE building_invitations
SET apartment_id = NULL,
    tenancy = NULL
WHERE role = 'STAFF'
  AND apartment_id IS NOT NULL;
