ALTER TABLE payments
    ADD COLUMN building_id UUID;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_building
        FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE RESTRICT;

CREATE INDEX idx_payments_building_status_paid_at
    ON payments(building_id, status, paid_at DESC);
