package com.sakena.notification.infrastructure.web.dto

import com.sakena.notification.domain.model.Notification
import com.sakena.notification.domain.model.NotificationType
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val type: NotificationType,
    val href: String?,
    val createdAt: Instant,
    val readAt: Instant?,
    val unread: Boolean,
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id.value,
            title = notification.title,
            body = notification.body,
            type = notification.type,
            href = notification.href,
            createdAt = notification.createdAt,
            readAt = notification.readAt,
            unread = notification.unread,
        )
    }
}

data class UnreadCountResponse(
    val count: Long,
)
