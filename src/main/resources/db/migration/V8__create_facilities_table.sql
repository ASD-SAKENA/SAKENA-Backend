CREATE TABLE IF NOT EXISTS facilities (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    icon VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
