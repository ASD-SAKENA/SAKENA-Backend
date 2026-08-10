ALTER TABLE service_requests
    ADD COLUMN requesting_apartment_id UUID;

ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_requesting_apartment
        FOREIGN KEY (requesting_apartment_id) REFERENCES apartments(id) ON DELETE RESTRICT;

CREATE INDEX idx_service_requests_requesting_apartment
    ON service_requests(requesting_apartment_id);
