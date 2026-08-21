-- An optional profile picture. Only the object-storage key is kept here; the
-- bytes live in MinIO and reach the browser through short-lived presigned
-- URLs, the same way chat attachments and payment receipts already work.
-- NULL means the user has not set one, and the UI keeps showing their initial.
ALTER TABLE users ADD COLUMN avatar_object_key VARCHAR(500);
