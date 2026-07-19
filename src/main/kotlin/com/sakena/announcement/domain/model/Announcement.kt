package com.sakena.announcement.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant

/**
 * Announcement aggregate root — a public notice published by the building
 * manager for all residents. Construction goes through [create] (new
 * announcements) or [reconstitute] (rehydration from persistence) so an
 * invalid Announcement can never exist.
 */
class Announcement private constructor(
    val id: AnnouncementId,
    val title: String,
    val body: String,
    val createdBy: UserId,
    val createdAt: Instant,
) {

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_BODY_LENGTH = 4_000

        fun create(title: String, body: String, createdBy: UserId): Announcement =
            Announcement(
                id = AnnouncementId.new(),
                title = validateTitle(title),
                body = validateBody(body),
                createdBy = createdBy,
                createdAt = Instant.now(),
            )

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: AnnouncementId,
            title: String,
            body: String,
            createdBy: UserId,
            createdAt: Instant,
        ): Announcement = Announcement(id, title, body, createdBy, createdAt)

        private fun validateTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Announcement title must not be blank")
            if (trimmed.length > MAX_TITLE_LENGTH) {
                throw DomainValidationException("Announcement title must be at most $MAX_TITLE_LENGTH characters")
            }
            return trimmed
        }

        private fun validateBody(body: String): String {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Announcement body must not be blank")
            if (trimmed.length > MAX_BODY_LENGTH) {
                throw DomainValidationException("Announcement body must be at most $MAX_BODY_LENGTH characters")
            }
            return trimmed
        }
    }
}
