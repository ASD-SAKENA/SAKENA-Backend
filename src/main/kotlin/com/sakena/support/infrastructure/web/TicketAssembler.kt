package com.sakena.support.infrastructure.web

import com.sakena.property.domain.ApartmentRepository
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.support.application.SupportTicketService
import com.sakena.support.domain.model.SupportTicket
import com.sakena.support.domain.model.TicketMessage
import com.sakena.support.infrastructure.web.dto.TicketMessageResponse
import com.sakena.support.infrastructure.web.dto.TicketResponse
import com.sakena.support.infrastructure.web.dto.TicketThreadResponse
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Component

/**
 * Turns tickets into the shape each viewer is allowed to see.
 *
 * Anonymity is enforced here and nowhere else: the resident's name and unit
 * are resolved only when the viewer is entitled to them, so no endpoint can
 * leak the identity by forgetting to check.
 */
@Component
class TicketAssembler(
    private val service: SupportTicketService,
    private val userRepository: UserRepository,
    private val residencyRepository: ResidencyRepository,
    private val apartmentRepository: ApartmentRepository,
) {

    fun toResponse(ticket: SupportTicket, viewer: User): TicketResponse {
        if (!mayKnowWhoRaised(ticket, viewer)) {
            return TicketResponse.from(ticket, raisedByName = null, raisedByUnit = null)
        }
        val raiser = userRepository.findById(ticket.raisedBy)
        val unit = residencyRepository.findActiveByResident(ticket.raisedBy)
            ?.let { apartmentRepository.findById(it.apartmentId)?.unitNumber }
        return TicketResponse.from(ticket, raisedByName = raiser?.username, raisedByUnit = unit)
    }

    fun toThread(ticket: SupportTicket, messages: List<TicketMessage>, viewer: User): TicketThreadResponse =
        TicketThreadResponse(
            ticket = toResponse(ticket, viewer),
            messages = messages.map { toMessage(it, ticket, viewer) },
        )

    fun toMessage(message: TicketMessage, ticket: SupportTicket, viewer: User): TicketMessageResponse =
        TicketMessageResponse.from(
            message = message,
            mine = message.authorId == viewer.id,
            // The only two participants are the raiser and the manager, so
            // anyone who is not the raiser is answering as the manager.
            byManager = message.authorId != ticket.raisedBy,
            attachmentUrl = service.attachmentUrl(message),
        )

    /**
     * The resident always sees their own ticket in full. A manager sees who
     * raised it unless the resident asked to stay anonymous.
     */
    private fun mayKnowWhoRaised(ticket: SupportTicket, viewer: User): Boolean =
        when (viewer.role) {
            Role.RESIDENT -> ticket.raisedBy == viewer.id
            Role.MANAGER -> !ticket.anonymous
            else -> false
        }
}
