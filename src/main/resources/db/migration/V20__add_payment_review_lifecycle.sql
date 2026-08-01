ALTER TABLE payments
    ADD COLUMN transaction_reference VARCHAR(100),
    ADD COLUMN receipt_object_key VARCHAR(500),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN reviewed_by UUID,
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN rejection_reason VARCHAR(500);

-- Existing rows were recorded as completed payments before the review workflow existed.
UPDATE payments
SET transaction_reference = 'legacy-' || id
WHERE transaction_reference IS NULL;

ALTER TABLE payments
    ALTER COLUMN transaction_reference SET NOT NULL,
    ALTER COLUMN status DROP DEFAULT,
    ADD CONSTRAINT uq_payments_transaction_reference UNIQUE (transaction_reference),
    ADD CONSTRAINT fk_payments_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE RESTRICT;
