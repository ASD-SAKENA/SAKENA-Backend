CREATE TABLE staff_ratings (
    id UUID PRIMARY KEY,
    service_request_id UUID NOT NULL UNIQUE REFERENCES service_requests(id),
    staff_id UUID NOT NULL REFERENCES users(id),
    resident_id UUID NOT NULL REFERENCES users(id),
    score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_staff_ratings_staff_id ON staff_ratings(staff_id);
