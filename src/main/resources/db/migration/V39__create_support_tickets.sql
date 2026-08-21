-- Private complaint / criticism / suggestion threads between a resident and
-- their building manager. Staff take no part in this feature.
CREATE TABLE IF NOT EXISTS support_tickets (
    id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    -- Always recorded, even for an anonymous ticket: replies and notifications
    -- have to reach someone. Anonymity hides this from the manager at the web
    -- boundary; it is never a gap in the record.
    raised_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- Drives the manager's queue ordering, so a busy thread stays on top.
    last_message_at TIMESTAMP NOT NULL
);

-- The manager's queue: newest conversation first, optionally filtered by status.
CREATE INDEX idx_support_tickets_building
    ON support_tickets(building_id, last_message_at DESC);

-- The resident's own list.
CREATE INDEX idx_support_tickets_raised_by
    ON support_tickets(raised_by, last_message_at DESC);

CREATE TABLE IF NOT EXISTS support_ticket_messages (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(10) NOT NULL,
    -- Text messages carry a body; image and voice messages carry an attachment.
    body VARCHAR(2000),
    attachment_key VARCHAR(500),
    attachment_content_type VARCHAR(120),
    attachment_size_bytes BIGINT,
    attachment_duration_seconds INT,
    sent_at TIMESTAMP NOT NULL
);

-- The thread is always read whole, oldest-first.
CREATE INDEX idx_support_ticket_messages_thread
    ON support_ticket_messages(ticket_id, sent_at);
