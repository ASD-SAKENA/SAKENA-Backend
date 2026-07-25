package com.sakena.chat.infrastructure.persistence

import com.sakena.chat.domain.model.ChatMessageKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA persistence model for chat messages. The attachment value object is
 * flattened into nullable columns — it is always loaded with its message.
 */
@Entity
@Table(name = "chat_messages")
class ChatMessageEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "building_id", nullable = false, updatable = false)
    var buildingId: UUID,

    @Column(name = "sender_id", nullable = false, updatable = false)
    var senderId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    var kind: ChatMessageKind,

    @Column(name = "body", length = 4_000)
    var body: String?,

    @Column(name = "attachment_key", length = 300)
    var attachmentKey: String?,

    @Column(name = "attachment_content_type", length = 100)
    var attachmentContentType: String?,

    @Column(name = "attachment_size_bytes")
    var attachmentSizeBytes: Long?,

    @Column(name = "attachment_duration_seconds")
    var attachmentDurationSeconds: Int?,

    @Column(name = "sent_at", nullable = false, updatable = false)
    var sentAt: Instant,

    @Column(name = "edited_at")
    var editedAt: Instant?,

    @Column(name = "deleted_at")
    var deletedAt: Instant?,

    @Column(name = "deleted_by")
    var deletedBy: UUID?,
)
