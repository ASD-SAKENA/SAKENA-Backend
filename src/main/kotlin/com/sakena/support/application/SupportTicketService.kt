package com.sakena.support.application

import com.sakena.notification.application.NotificationService
import com.sakena.notification.domain.model.NotificationType
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.support.application.command.OpenTicketCommand
import com.sakena.support.application.command.ReplyToTicketCommand
import com.sakena.support.application.command.TicketAttachmentUpload
import com.sakena.support.domain.SupportTicketRepository
import com.sakena.support.domain.TicketAttachmentStorage
import com.sakena.support.domain.TicketMessageRepository
import com.sakena.support.domain.TicketNotFoundException
import com.sakena.support.domain.model.SupportTicket
import com.sakena.support.domain.model.TicketAttachment
import com.sakena.support.domain.model.TicketId
import com.sakena.support.domain.model.TicketMessage
import com.sakena.support.domain.model.TicketMessageKind
import com.sakena.support.domain.model.TicketStatus
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Private complaint, criticism and suggestion threads between a resident and
 * their building manager.
 *
 * Staff take no part: every entry point rejects any role other than RESIDENT
 * and MANAGER, so a staff account can neither open a ticket nor read one.
 */
@Service
@Transactional
class SupportTicketService(
    private val ticketRepository: SupportTicketRepository,
    private val messageRepository: TicketMessageRepository,
    private val buildingAccess: BuildingAccess,
    private val notificationService: NotificationService,
    private val attachmentStorage: TicketAttachmentStorage,
) {

    companion object {
        const val MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        private val ALLOWED_AUDIO_TYPES =
            setOf("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav")
    }

    fun open(command: OpenTicketCommand, requestedBy: User): SupportTicket {
        if (requestedBy.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can open a support ticket")
        }
        val buildingId = buildingAccess.residentBuildingId(requestedBy.id)
        val ticket = ticketRepository.save(
            SupportTicket.open(
                buildingId = buildingId,
                raisedBy = requestedBy.id,
                category = command.category,
                subject = command.subject,
                anonymous = command.anonymous,
            ),
        )
        messageRepository.save(TicketMessage.text(ticket.id, requestedBy.id, command.body))
        notifyManager(ticket, "تیکت جدید در «پشتیبانی»", ticket.subject)
        return ticket
    }

    fun reply(ticketId: TicketId, command: ReplyToTicketCommand, requestedBy: User): TicketMessage {
        val ticket = requireParticipant(ticketId, requestedBy)
        val message = messageRepository.save(toMessage(ticket.id, command, requestedBy))
        ticket.recordReply(requestedBy.role)
        ticketRepository.save(ticket)

        if (requestedBy.role == Role.MANAGER) {
            notificationService.notifyUser(
                recipientId = ticket.raisedBy,
                title = "پاسخ مدیر به تیکت شما",
                body = ticket.subject,
                type = NotificationType.SYSTEM,
                href = "/support",
            )
        } else {
            notifyManager(ticket, "پاسخ تازه در یک تیکت", ticket.subject)
        }
        return message
    }

    /** The manager declares the matter handled. */
    fun markAnswered(ticketId: TicketId, requestedBy: User): SupportTicket {
        if (requestedBy.role != Role.MANAGER) {
            throw DomainForbiddenException("Only the building manager can answer a ticket")
        }
        val ticket = requireParticipant(ticketId, requestedBy)
        ticket.markAnswered()
        notificationService.notifyUser(
            recipientId = ticket.raisedBy,
            title = "تیکت شما پاسخ داده شد",
            body = ticket.subject,
            type = NotificationType.SYSTEM,
            href = "/support",
        )
        return ticketRepository.save(ticket)
    }

    @Transactional(readOnly = true)
    fun getMine(requestedBy: User): List<SupportTicket> {
        if (requestedBy.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents have their own support tickets")
        }
        return ticketRepository.findAllByResident(requestedBy.id)
    }

    @Transactional(readOnly = true)
    fun getForBuilding(status: TicketStatus?, requestedBy: User): List<SupportTicket> {
        if (requestedBy.role != Role.MANAGER) {
            throw DomainForbiddenException("Only the building manager can list a building's tickets")
        }
        return ticketRepository.findAllByBuilding(managedBuilding(requestedBy), status)
    }

    @Transactional(readOnly = true)
    fun getThread(ticketId: TicketId, requestedBy: User): Pair<SupportTicket, List<TicketMessage>> {
        val ticket = requireParticipant(ticketId, requestedBy)
        return ticket to messageRepository.findAllByTicket(ticket.id)
    }

    /**
     * Stores an attachment for a ticket the caller takes part in and returns
     * its storage key, which the follow-up reply references.
     */
    fun uploadAttachment(
        ticketId: TicketId,
        kind: TicketMessageKind,
        upload: TicketAttachmentUpload,
        requestedBy: User,
    ): String {
        val ticket = requireParticipant(ticketId, requestedBy)
        validateUpload(kind, upload)
        return attachmentStorage.store(
            buildingId = ticket.buildingId,
            originalFilename = upload.originalFilename,
            contentType = upload.contentType,
            sizeBytes = upload.sizeBytes,
            content = upload.content,
        )
    }

    /** Attachments are private in storage, so the client gets a short-lived URL. */
    fun attachmentUrl(message: TicketMessage): String? =
        message.attachment?.let { attachmentStorage.presignedUrl(it.storageKey) }

    private fun validateUpload(kind: TicketMessageKind, upload: TicketAttachmentUpload) {
        if (upload.sizeBytes <= 0) {
            throw DomainValidationException("Attachment must not be empty")
        }
        if (upload.sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw DomainValidationException("Attachment must be at most 15 MB")
        }
        val allowed = when (kind) {
            TicketMessageKind.IMAGE -> ALLOWED_IMAGE_TYPES
            TicketMessageKind.VOICE -> ALLOWED_AUDIO_TYPES
            TicketMessageKind.TEXT -> throw DomainValidationException("Attachment kind must be IMAGE or VOICE")
        }
        // The browser sometimes appends codec parameters, e.g. "audio/webm;codecs=opus".
        val baseType = upload.contentType.substringBefore(';').trim().lowercase()
        if (baseType !in allowed) {
            throw DomainValidationException("Unsupported attachment type '$baseType'")
        }
    }

    /**
     * A ticket is visible to exactly two people: the resident who raised it and
     * the manager of its building. Everyone else — including a resident of the
     * same building — is refused.
     */
    private fun requireParticipant(ticketId: TicketId, requestedBy: User): SupportTicket {
        val ticket = ticketRepository.findById(ticketId) ?: throw TicketNotFoundException(ticketId)
        val allowed = when (requestedBy.role) {
            Role.RESIDENT -> ticket.raisedBy == requestedBy.id
            Role.MANAGER -> ticket.buildingId == managedBuilding(requestedBy)
            else -> false
        }
        if (!allowed) {
            throw DomainForbiddenException("You do not take part in this support ticket")
        }
        return ticket
    }

    private fun managedBuilding(manager: User): BuildingId =
        manager.managedBuildingId
            ?: throw DomainForbiddenException("You do not manage a building")

    private fun notifyManager(ticket: SupportTicket, title: String, body: String) {
        notificationService.notifyBuildingManagers(
            buildingId = ticket.buildingId,
            title = title,
            body = body,
            type = NotificationType.SYSTEM,
            href = "/support",
        )
    }

    private fun toMessage(
        ticketId: TicketId,
        command: ReplyToTicketCommand,
        author: User,
    ): TicketMessage {
        if (command.kind == TicketMessageKind.TEXT) {
            val body = command.body
                ?: throw DomainValidationException("A text message must have a body")
            return TicketMessage.text(ticketId, author.id, body)
        }
        val attachment = TicketAttachment(
            storageKey = command.storageKey
                ?: throw DomainValidationException("An attachment message must reference stored content"),
            contentType = command.contentType
                ?: throw DomainValidationException("An attachment message must have a content type"),
            sizeBytes = command.sizeBytes
                ?: throw DomainValidationException("An attachment message must have a size"),
            durationSeconds = command.durationSeconds,
        )
        return TicketMessage.withAttachment(ticketId, author.id, command.kind, attachment)
    }
}
