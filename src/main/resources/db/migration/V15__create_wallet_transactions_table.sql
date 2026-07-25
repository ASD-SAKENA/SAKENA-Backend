CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    direction VARCHAR(10) NOT NULL,
    category VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    description VARCHAR(300) NOT NULL,
    -- Balance right after the transaction, so the ledger reads like a statement.
    balance_after NUMERIC(18, 2) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_wallet_transactions_wallet_occurred_at
    ON wallet_transactions(wallet_id, occurred_at DESC);
