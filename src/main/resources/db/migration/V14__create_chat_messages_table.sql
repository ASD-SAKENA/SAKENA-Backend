CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    kind VARCHAR(10) NOT NULL,
    body VARCHAR(4000),
    attachment_key VARCHAR(300),
    attachment_content_type VARCHAR(100),
    attachment_size_bytes BIGINT,
    attachment_duration_seconds INTEGER,
    sent_at TIMESTAMP NOT NULL,
    edited_at TIMESTAMP,
    -- Deletion is a tombstone so the conversation keeps its shape.
    deleted_at TIMESTAMP,
    deleted_by UUID
);

CREATE INDEX idx_chat_messages_building_sent_at ON chat_messages(building_id, sent_at DESC);
