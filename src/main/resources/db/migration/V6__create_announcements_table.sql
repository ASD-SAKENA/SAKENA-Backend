CREATE TABLE IF NOT EXISTS announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_announcements_created_at ON announcements(created_at DESC);
