package com.sakena.notification.application

import com.sakena.notification.domain.NotificationNotFoundException
import com.sakena.notification.domain.NotificationRepository
import com.sakena.notification.domain.model.Notification
import com.sakena.notification.domain.model.NotificationId
import com.sakena.notification.domain.model.NotificationType
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val residencyRepository: ResidencyRepository,
    private val userRepository: UserRepository,
) {

    fun notifyUser(
        recipientId: UserId,
        title: String,
        body: String,
        type: NotificationType,
        href: String? = null,
    ): Notification =
        notificationRepository.save(
            Notification.create(recipientId, title, body, type, href),
        )

    /** One notification per active resident of the building (e.g. a new announcement). */
    fun notifyBuildingResidents(
        buildingId: BuildingId,
        title: String,
        body: String,
        type: NotificationType,
        href: String? = null,
    ): List<Notification> {
        val recipientIds = residencyRepository.findActiveByBuilding(buildingId)
            .map { it.residentId }
            .distinct()
        if (recipientIds.isEmpty()) return emptyList()
        return notificationRepository.saveAll(
            recipientIds.map { Notification.create(it, title, body, type, href) },
        )
    }

    /** One notification per manager administering the building. */
    fun notifyBuildingManagers(
        buildingId: BuildingId,
        title: String,
        body: String,
        type: NotificationType,
        href: String? = null,
    ): List<Notification> {
        val recipientIds = userRepository.findManagersOfBuilding(buildingId).map { it.id }
        if (recipientIds.isEmpty()) return emptyList()
        return notificationRepository.saveAll(
            recipientIds.map { Notification.create(it, title, body, type, href) },
        )
    }

    @Transactional(readOnly = true)
    fun listFor(recipientId: UserId, limit: Int = DEFAULT_LIMIT): List<Notification> =
        notificationRepository.findNewestForRecipient(recipientId, limit.coerceIn(1, MAX_LIMIT))

    @Transactional(readOnly = true)
    fun unreadCount(recipientId: UserId): Long =
        notificationRepository.countUnread(recipientId)

    fun markRead(id: NotificationId, recipientId: UserId): Notification {
        val notification = notificationRepository.findById(id)
            ?: throw NotificationNotFoundException(id)
        if (notification.recipientId != recipientId) {
            throw DomainForbiddenException("You cannot mark another user's notification as read")
        }
        notification.markRead()
        return notificationRepository.save(notification)
    }

    fun markAllRead(recipientId: UserId) {
        notificationRepository.markAllRead(recipientId)
    }

    companion object {
        const val DEFAULT_LIMIT = 30
        const val MAX_LIMIT = 100
    }
}
