ALTER TABLE payments
    ADD CONSTRAINT fk_payments_payer
    FOREIGN KEY (payer_id) REFERENCES users(id) ON DELETE RESTRICT;
