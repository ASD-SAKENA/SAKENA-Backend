ALTER TABLE wallets
    ADD COLUMN building_id UUID NULL REFERENCES buildings (id);

-- Every existing manager gets their own building wallet, migrated from
-- whatever balance the old single shared account held — split evenly is not
-- meaningful, so each new wallet starts at zero and the historical shared
-- balance stays on the original (now orphaned) row for manual reconciliation
-- if this runs against an environment with real balances.
INSERT INTO wallets (id, owner_type, owner_user_id, building_id, balance, created_at, updated_at)
SELECT gen_random_uuid(), 'BUILDING', NULL, u.managed_building_id, 0, now(), now()
FROM users u
WHERE u.managed_building_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM wallets w WHERE w.building_id = u.managed_building_id
  );

CREATE UNIQUE INDEX idx_wallets_building_id ON wallets (building_id) WHERE building_id IS NOT NULL;
