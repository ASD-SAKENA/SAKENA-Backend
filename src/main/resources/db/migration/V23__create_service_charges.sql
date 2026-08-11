CREATE TABLE service_charges (
    id UUID PRIMARY KEY,
    source_service_request_id UUID NOT NULL,
    building_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_apartment_id UUID,
    charge_period_id UUID,
    created_at TIMESTAMP NOT NULL,
    attached_at TIMESTAMP,
    CONSTRAINT uq_service_charges_source_request UNIQUE (source_service_request_id),
    CONSTRAINT fk_service_charges_source_request
        FOREIGN KEY (source_service_request_id) REFERENCES service_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_charges_building
        FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_charges_target_apartment
        FOREIGN KEY (target_apartment_id) REFERENCES apartments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_charges_period
        FOREIGN KEY (charge_period_id) REFERENCES charge_periods(id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_charges_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_service_charges_target CHECK (
        (target_type = 'ALL_UNITS' AND target_apartment_id IS NULL)
        OR (target_type = 'SPECIFIC_UNIT' AND target_apartment_id IS NOT NULL)
    ),
    CONSTRAINT ck_service_charges_attachment CHECK (
        (charge_period_id IS NULL AND attached_at IS NULL)
        OR (charge_period_id IS NOT NULL AND attached_at IS NOT NULL)
    )
);

CREATE INDEX idx_service_charges_pending_building
    ON service_charges(building_id, created_at)
    WHERE charge_period_id IS NULL;
