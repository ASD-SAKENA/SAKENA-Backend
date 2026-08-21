package com.sakena.support.domain.model

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupportTicketTest {

    private val buildingId = BuildingId.new()
    private val residentId = UserId(UUID.randomUUID())

    private fun ticket(anonymous: Boolean = false) = SupportTicket.open(
        buildingId = buildingId,
        raisedBy = residentId,
        category = TicketCategory.COMPLAINT,
        subject = "  سر و صدای واحد بالا  ",
        anonymous = anonymous,
    )

    @Test
    fun `a new ticket waits for the manager and trims its subject`() {
        val ticket = ticket()

        assertEquals(TicketStatus.AWAITING_REPLY, ticket.status)
        assertEquals("سر و صدای واحد بالا", ticket.subject)
    }

    @Test
    fun `a manager's reply puts the ticket in progress`() {
        val ticket = ticket()

        ticket.recordReply(Role.MANAGER)

        assertEquals(TicketStatus.IN_PROGRESS, ticket.status)
    }

    @Test
    fun `the manager can declare the matter answered`() {
        val ticket = ticket()
        ticket.recordReply(Role.MANAGER)

        ticket.markAnswered()

        assertTrue(ticket.answered)
    }

    @Test
    fun `a resident replying to an answered ticket reopens it`() {
        // Otherwise a dissatisfied resident has to file a duplicate ticket and
        // the manager loses the history of what was already discussed.
        val ticket = ticket()
        ticket.recordReply(Role.MANAGER)
        ticket.markAnswered()

        ticket.recordReply(Role.RESIDENT)

        assertEquals(TicketStatus.AWAITING_REPLY, ticket.status)
    }

    @Test
    fun `answering twice is refused`() {
        val ticket = ticket()
        ticket.markAnswered()

        assertFailsWith<DomainConflictException> { ticket.markAnswered() }
    }

    @Test
    fun `staff take no part in a support ticket`() {
        val ticket = ticket()

        assertFailsWith<DomainConflictException> { ticket.recordReply(Role.STAFF) }
        assertFailsWith<DomainConflictException> { ticket.recordReply(Role.ADMIN) }
    }

    @Test
    fun `a reply moves the ticket to the top of the queue`() {
        val ticket = ticket()
        val before = ticket.lastMessageAt

        ticket.recordReply(Role.MANAGER, now = before.plusSeconds(60))

        assertEquals(before.plusSeconds(60), ticket.lastMessageAt)
    }

    @Test
    fun `an anonymous ticket still records who raised it`() {
        // Anonymity hides the resident from the manager; it cannot lose the
        // link, or replies and notifications would have nowhere to go.
        val ticket = ticket(anonymous = true)

        assertTrue(ticket.anonymous)
        assertEquals(residentId, ticket.raisedBy)
    }

    @Test
    fun `a blank or overlong subject is refused`() {
        assertFailsWith<DomainValidationException> {
            SupportTicket.open(buildingId, residentId, TicketCategory.SUGGESTION, "   ", false)
        }
        assertFailsWith<DomainValidationException> {
            SupportTicket.open(
                buildingId, residentId, TicketCategory.SUGGESTION,
                "x".repeat(SupportTicket.MAX_SUBJECT_LENGTH + 1), false,
            )
        }
    }
}
