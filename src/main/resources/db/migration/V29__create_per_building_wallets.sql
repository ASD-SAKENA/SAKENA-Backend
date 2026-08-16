ALTER TABLE wallets
    ADD COLUMN owner_building_id UUID;

ALTER TABLE wallets
    ADD CONSTRAINT fk_wallets_owner_building
        FOREIGN KEY (owner_building_id) REFERENCES buildings(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_wallets_owner_building
    ON wallets(owner_building_id)
    WHERE owner_building_id IS NOT NULL;

-- The original seeded BUILDING wallet has no reliable building owner and is
-- retained only as legacy data. A scoped wallet is created lazily for each
-- building the first time its manager uses it.
