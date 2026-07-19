CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    payer_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    paid_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_payments_payer_paid_at ON payments(payer_id, paid_at DESC);
