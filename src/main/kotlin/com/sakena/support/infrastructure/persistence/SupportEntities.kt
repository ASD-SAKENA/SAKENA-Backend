package com.sakena.support.infrastructure.persistence

import com.sakena.support.domain.model.TicketCategory
import com.sakena.support.domain.model.TicketMessageKind
import com.sakena.support.domain.model.TicketStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "support_tickets")
class SupportTicketEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    @Column(name = "building_id", nullable = false)
    var buildingId: UUID,

    @Column(name = "raised_by", nullable = false)
    var raisedBy: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    var category: TicketCategory,

    @Column(name = "subject", nullable = false, length = 150)
    var subject: String,

    @Column(name = "anonymous", nullable = false)
    var anonymous: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TicketStatus,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Column(name = "last_message_at", nullable = false)
    var lastMessageAt: Instant,
)

@Entity
@Table(name = "support_ticket_messages")
class TicketMessageEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    @Column(name = "ticket_id", nullable = false)
    var ticketId: UUID,

    @Column(name = "author_id", nullable = false)
    var authorId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    var kind: TicketMessageKind,

    @Column(name = "body", length = 2000)
    var body: String?,

    @Column(name = "attachment_key", length = 500)
    var attachmentKey: String?,

    @Column(name = "attachment_content_type", length = 120)
    var attachmentContentType: String?,

    @Column(name = "attachment_size_bytes")
    var attachmentSizeBytes: Long?,

    @Column(name = "attachment_duration_seconds")
    var attachmentDurationSeconds: Int?,

    @Column(name = "sent_at", nullable = false)
    var sentAt: Instant,
)
