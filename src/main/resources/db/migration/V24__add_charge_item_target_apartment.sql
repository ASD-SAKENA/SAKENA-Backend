ALTER TABLE charge_items
    ADD COLUMN target_apartment_id UUID;

ALTER TABLE charge_items
    ADD CONSTRAINT fk_charge_items_target_apartment
        FOREIGN KEY (target_apartment_id) REFERENCES apartments(id) ON DELETE RESTRICT;

ALTER TABLE charge_items
    ADD CONSTRAINT ck_charge_items_allocation_target CHECK (
        (allocation = 'SPECIFIC_UNIT' AND target_apartment_id IS NOT NULL)
        OR (allocation IN ('EQUAL', 'BY_AREA') AND target_apartment_id IS NULL)
    );
