package com.sakena.support.domain

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.support.domain.model.SupportTicket
import com.sakena.support.domain.model.TicketId
import com.sakena.support.domain.model.TicketMessage
import com.sakena.support.domain.model.TicketStatus
import com.sakena.user.domain.UserId

/** Outbound port for persisting support tickets. */
interface SupportTicketRepository {
    fun save(ticket: SupportTicket): SupportTicket

    fun findById(id: TicketId): SupportTicket?

    /** A resident's own tickets, freshest conversation first. */
    fun findAllByResident(residentId: UserId): List<SupportTicket>

    /** The building's tickets for its manager, optionally narrowed by status. */
    fun findAllByBuilding(buildingId: BuildingId, status: TicketStatus?): List<SupportTicket>
}

/** Outbound port for persisting the messages of a ticket's thread. */
interface TicketMessageRepository {
    fun save(message: TicketMessage): TicketMessage

    /** The whole thread, oldest-first for rendering. */
    fun findAllByTicket(ticketId: TicketId): List<TicketMessage>
}

/**
 * Outbound port for the object storage holding ticket attachments. Keeps MinIO
 * out of the domain and application layers.
 */
interface TicketAttachmentStorage {
    /** Stores the bytes and returns the storage key they were written under. */
    fun store(
        buildingId: BuildingId,
        originalFilename: String?,
        contentType: String,
        sizeBytes: Long,
        content: java.io.InputStream,
    ): String

    /** A short-lived URL the browser can use to fetch the object directly. */
    fun presignedUrl(storageKey: String): String

    fun delete(storageKey: String)
}

class TicketNotFoundException(id: TicketId) :
    EntityNotFoundException("Support ticket with id '$id' was not found")
