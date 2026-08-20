package com.sakena.notification.infrastructure.persistence

import com.sakena.notification.domain.NotificationRepository
import com.sakena.notification.domain.model.Notification
import com.sakena.notification.domain.model.NotificationId
import com.sakena.user.domain.UserId
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

interface NotificationJpaRepository : JpaRepository<NotificationEntity, UUID> {

    fun findByRecipientIdOrderByCreatedAtDesc(
        recipientId: UUID,
        pageable: org.springframework.data.domain.Pageable,
    ): List<NotificationEntity>

    fun countByRecipientIdAndReadAtIsNull(recipientId: UUID): Long

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE NotificationEntity n
        SET n.readAt = :readAt
        WHERE n.recipientId = :recipientId AND n.readAt IS NULL
        """,
    )
    fun markAllRead(
        @Param("recipientId") recipientId: UUID,
        @Param("readAt") readAt: Instant,
    ): Int
}

@Component
class NotificationRepositoryAdapter(
    private val jpaRepository: NotificationJpaRepository,
) : NotificationRepository {

    override fun save(notification: Notification): Notification {
        jpaRepository.save(toEntity(notification))
        return notification
    }

    override fun saveAll(notifications: List<Notification>): List<Notification> {
        if (notifications.isEmpty()) return emptyList()
        jpaRepository.saveAll(notifications.map(::toEntity))
        return notifications
    }

    override fun findById(id: NotificationId): Notification? =
        jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findNewestForRecipient(recipientId: UserId, limit: Int): List<Notification> =
        jpaRepository
            .findByRecipientIdOrderByCreatedAtDesc(recipientId.value, PageRequest.of(0, limit))
            .map(::toDomain)

    override fun countUnread(recipientId: UserId): Long =
        jpaRepository.countByRecipientIdAndReadAtIsNull(recipientId.value)

    override fun markAllRead(recipientId: UserId) {
        jpaRepository.markAllRead(recipientId.value, Instant.now())
    }

    private fun toEntity(notification: Notification) = NotificationEntity(
        id = notification.id.value,
        recipientId = notification.recipientId.value,
        title = notification.title,
        body = notification.body,
        type = notification.type,
        href = notification.href,
        createdAt = notification.createdAt,
        readAt = notification.readAt,
    )

    private fun toDomain(entity: NotificationEntity) = Notification.reconstitute(
        id = NotificationId(entity.id),
        recipientId = UserId(entity.recipientId),
        title = entity.title,
        body = entity.body,
        type = entity.type,
        href = entity.href,
        createdAt = entity.createdAt,
        readAt = entity.readAt,
    )
}
