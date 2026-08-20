package com.sakena.notification.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

@JvmInline
value class NotificationId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new(): NotificationId = NotificationId(UUID.randomUUID())

        fun from(raw: String): NotificationId =
            try {
                NotificationId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid notification id")
            }
    }
}

enum class NotificationType {
    ANNOUNCEMENT,
    SERVICE_REQUEST,
    BILLING,
    SYSTEM,
}

/**
 * In-app notification for a single recipient. Marking as read is the only
 * state transition after create — notifications are never edited or deleted
 * by the recipient (they age out of the list by pagination).
 */
class Notification private constructor(
    val id: NotificationId,
    val recipientId: UserId,
    val title: String,
    val body: String,
    val type: NotificationType,
    val href: String?,
    val createdAt: Instant,
    readAt: Instant?,
) {
    var readAt: Instant? = readAt
        private set

    val unread: Boolean get() = readAt == null

    fun markRead(at: Instant = Instant.now()) {
        if (readAt == null) readAt = at
    }

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_BODY_LENGTH = 1000
        const val MAX_HREF_LENGTH = 300

        fun create(
            recipientId: UserId,
            title: String,
            body: String,
            type: NotificationType,
            href: String? = null,
        ): Notification = Notification(
            id = NotificationId.new(),
            recipientId = recipientId,
            title = validateTitle(title),
            body = validateBody(body),
            type = type,
            href = validateHref(href),
            createdAt = Instant.now(),
            readAt = null,
        )

        fun reconstitute(
            id: NotificationId,
            recipientId: UserId,
            title: String,
            body: String,
            type: NotificationType,
            href: String?,
            createdAt: Instant,
            readAt: Instant?,
        ): Notification = Notification(id, recipientId, title, body, type, href, createdAt, readAt)

        private fun validateTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Notification title must not be blank")
            if (trimmed.length > MAX_TITLE_LENGTH) {
                throw DomainValidationException("Notification title must be at most $MAX_TITLE_LENGTH characters")
            }
            return trimmed
        }

        private fun validateBody(body: String): String {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Notification body must not be blank")
            if (trimmed.length > MAX_BODY_LENGTH) {
                throw DomainValidationException("Notification body must be at most $MAX_BODY_LENGTH characters")
            }
            return trimmed
        }

        private fun validateHref(href: String?): String? {
            val trimmed = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.length > MAX_HREF_LENGTH) {
                throw DomainValidationException("Notification href must be at most $MAX_HREF_LENGTH characters")
            }
            return trimmed
        }
    }
}
