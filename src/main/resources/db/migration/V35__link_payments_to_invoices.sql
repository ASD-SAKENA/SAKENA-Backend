-- Tie resident payment claims to the unit invoice they settle, so confirmation
-- can update the bill instead of floating as an orphaned title+amount.
ALTER TABLE payments
    ADD COLUMN invoice_id UUID REFERENCES unit_invoices (id);

CREATE INDEX idx_payments_invoice_id ON payments (invoice_id);
