package com.sakena.notification.domain

import com.sakena.notification.domain.model.Notification
import com.sakena.notification.domain.model.NotificationId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.UserId

interface NotificationRepository {
    fun save(notification: Notification): Notification

    fun saveAll(notifications: List<Notification>): List<Notification>

    fun findById(id: NotificationId): Notification?

    /** Newest first, capped so the bell panel stays cheap to load. */
    fun findNewestForRecipient(recipientId: UserId, limit: Int): List<Notification>

    fun countUnread(recipientId: UserId): Long

    fun markAllRead(recipientId: UserId)
}

class NotificationNotFoundException(id: NotificationId) :
    EntityNotFoundException("Notification with id '$id' was not found")
