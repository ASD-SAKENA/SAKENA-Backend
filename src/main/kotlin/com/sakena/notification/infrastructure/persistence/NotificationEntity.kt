package com.sakena.notification.infrastructure.persistence

import com.sakena.notification.domain.model.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications")
class NotificationEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "recipient_id", nullable = false, updatable = false)
    var recipientId: UUID,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "body", nullable = false, length = 1000)
    var body: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    var type: NotificationType,

    @Column(name = "href", length = 300)
    var href: String?,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "read_at")
    var readAt: Instant?,
)
