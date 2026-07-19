CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(20) NOT NULL,
    owner_user_id UUID UNIQUE,
    balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- The single shared building account wages are paid from.
INSERT INTO wallets (id, owner_type, owner_user_id, balance, created_at, updated_at)
VALUES (gen_random_uuid(), 'BUILDING', NULL, 0, now(), now());
