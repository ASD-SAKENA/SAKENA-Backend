package com.sakena.support.infrastructure.web.dto

import com.sakena.support.application.command.OpenTicketCommand
import com.sakena.support.application.command.ReplyToTicketCommand
import com.sakena.support.domain.model.SupportTicket
import com.sakena.support.domain.model.TicketCategory
import com.sakena.support.domain.model.TicketMessage
import com.sakena.support.domain.model.TicketMessageKind
import com.sakena.support.domain.model.TicketStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class OpenTicketRequest(
    @field:NotNull(message = "category must not be null")
    val category: TicketCategory,

    @field:NotBlank(message = "subject must not be blank")
    @field:Size(max = 150, message = "subject must be at most 150 characters")
    val subject: String,

    @field:NotBlank(message = "body must not be blank")
    @field:Size(max = 2000, message = "body must be at most 2000 characters")
    val body: String,

    /** Hides the resident's identity from the manager; fixed at creation. */
    val anonymous: Boolean = false,
) {
    fun toCommand() = OpenTicketCommand(
        category = category,
        subject = subject,
        body = body,
        anonymous = anonymous,
    )
}

data class ReplyRequest(
    @field:Size(max = 2000, message = "body must be at most 2000 characters")
    val body: String? = null,

    val kind: TicketMessageKind = TicketMessageKind.TEXT,
    val storageKey: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val durationSeconds: Int? = null,
) {
    fun toCommand() = ReplyToTicketCommand(
        body = body,
        kind = kind,
        storageKey = storageKey,
        contentType = contentType,
        sizeBytes = sizeBytes,
        durationSeconds = durationSeconds,
    )
}

/** The storage key a freshly uploaded attachment was written under. */
data class TicketAttachmentResponse(
    val storageKey: String,
    val contentType: String,
    val sizeBytes: Long,
)

/**
 * A ticket as one side sees it.
 *
 * [raisedByName] and [raisedByUnit] are null whenever the viewer must not know
 * who raised it — this is the single place anonymity is applied, so a new
 * endpoint cannot leak the identity by forgetting to check.
 */
data class TicketResponse(
    val id: UUID,
    val category: TicketCategory,
    val subject: String,
    val status: TicketStatus,
    val anonymous: Boolean,
    val raisedByName: String?,
    val raisedByUnit: String?,
    val createdAt: Instant,
    val lastMessageAt: Instant,
) {
    companion object {
        fun from(
            ticket: SupportTicket,
            raisedByName: String?,
            raisedByUnit: String?,
        ) = TicketResponse(
            id = ticket.id.value,
            category = ticket.category,
            subject = ticket.subject,
            status = ticket.status,
            anonymous = ticket.anonymous,
            raisedByName = raisedByName,
            raisedByUnit = raisedByUnit,
            createdAt = ticket.createdAt,
            lastMessageAt = ticket.lastMessageAt,
        )
    }
}

/**
 * One turn in the thread. [mine] lets the client align the bubble without
 * knowing user ids, which also keeps an anonymous author unidentifiable.
 */
data class TicketMessageResponse(
    val id: UUID,
    val kind: TicketMessageKind,
    val body: String?,
    val authorRole: String,
    val mine: Boolean,
    val attachmentUrl: String?,
    val durationSeconds: Int?,
    val sentAt: Instant,
) {
    companion object {
        fun from(
            message: TicketMessage,
            mine: Boolean,
            byManager: Boolean,
            attachmentUrl: String?,
        ) = TicketMessageResponse(
            id = message.id.value,
            kind = message.kind,
            body = message.body,
            authorRole = if (byManager) "MANAGER" else "RESIDENT",
            mine = mine,
            attachmentUrl = attachmentUrl,
            durationSeconds = message.attachment?.durationSeconds,
            sentAt = message.sentAt,
        )
    }
}

data class TicketThreadResponse(
    val ticket: TicketResponse,
    val messages: List<TicketMessageResponse>,
)
