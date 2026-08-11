CREATE TABLE IF NOT EXISTS polls (
    id UUID PRIMARY KEY,
    question VARCHAR(300) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP
);

CREATE INDEX idx_polls_created_at ON polls(created_at DESC);

CREATE TABLE IF NOT EXISTS poll_options (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    label VARCHAR(200) NOT NULL,
    position INTEGER NOT NULL
);

CREATE INDEX idx_poll_options_poll ON poll_options(poll_id, position);

CREATE TABLE IF NOT EXISTS poll_votes (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    voter_id UUID NOT NULL,
    cast_at TIMESTAMP NOT NULL,
    -- One vote per resident per poll, enforced by the database.
    CONSTRAINT uq_poll_votes_poll_voter UNIQUE (poll_id, voter_id)
);

CREATE INDEX idx_poll_votes_option ON poll_votes(option_id);
