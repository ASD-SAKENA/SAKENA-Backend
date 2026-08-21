package com.sakena.support.application

import com.sakena.notification.application.NotificationService
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
import com.sakena.support.domain.model.SupportTicket
import com.sakena.support.domain.model.TicketCategory
import com.sakena.support.domain.model.TicketMessage
import com.sakena.support.domain.model.TicketMessageKind
import com.sakena.support.domain.model.TicketStatus
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupportTicketServiceTest {

    private val ticketRepository = mockk<SupportTicketRepository>()
    private val messageRepository = mockk<TicketMessageRepository>(relaxed = true)
    private val buildingAccess = mockk<BuildingAccess>()
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val attachmentStorage = mockk<TicketAttachmentStorage>(relaxed = true)
    private val service = SupportTicketService(
        ticketRepository, messageRepository, buildingAccess, notificationService, attachmentStorage,
    )

    private val buildingId = BuildingId.new()
    private val otherBuildingId = BuildingId.new()

    private fun user(role: Role, managed: BuildingId? = null) = User.reconstitute(
        id = UserId(UUID.randomUUID()),
        username = "u-${UUID.randomUUID().toString().take(6)}",
        email = "${UUID.randomUUID().toString().take(6)}@example.com",
        passwordHash = "hash",
        role = role,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        active = true,
        managedBuildingId = managed,
    )

    private val resident = user(Role.RESIDENT)
    private val manager = user(Role.MANAGER, buildingId)
    private val staff = user(Role.STAFF)

    private fun ticket(
        raisedBy: UserId = resident.id,
        building: BuildingId = buildingId,
        anonymous: Boolean = false,
    ) = SupportTicket.open(building, raisedBy, TicketCategory.COMPLAINT, "سر و صدا", anonymous)

    private val openCommand = OpenTicketCommand(
        category = TicketCategory.COMPLAINT,
        subject = "سر و صدا",
        body = "هر شب تا دیروقت صدا می‌آید",
        anonymous = false,
    )

    private fun textReply(body: String = "پیگیری می‌کنم") = ReplyToTicketCommand(
        body = body, kind = TicketMessageKind.TEXT,
        storageKey = null, contentType = null, sizeBytes = null, durationSeconds = null,
    )

    @Test
    fun `a resident opens a ticket and its first message is stored`() {
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { ticketRepository.save(any()) } answers { firstArg() }
        val saved = slot<TicketMessage>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        val ticket = service.open(openCommand, resident)

        assertEquals(TicketStatus.AWAITING_REPLY, ticket.status)
        assertEquals("هر شب تا دیروقت صدا می‌آید", saved.captured.body)
        verify(exactly = 1) { notificationService.notifyBuildingManagers(buildingId, any(), any(), any(), any()) }
    }

    @Test
    fun `staff cannot open a ticket`() {
        assertFailsWith<DomainForbiddenException> { service.open(openCommand, staff) }
    }

    @Test
    fun `a manager cannot open a ticket`() {
        assertFailsWith<DomainForbiddenException> { service.open(openCommand, manager) }
    }

    @Test
    fun `staff cannot read a ticket thread`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainForbiddenException> { service.getThread(ticket.id, staff) }
    }

    @Test
    fun `another resident of the same building cannot read the ticket`() {
        // A ticket is private between its author and the manager, so being a
        // neighbour is not enough.
        val ticket = ticket()
        val neighbour = user(Role.RESIDENT)
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainForbiddenException> { service.getThread(ticket.id, neighbour) }
    }

    @Test
    fun `a manager of another building cannot read the ticket`() {
        val ticket = ticket()
        val otherManager = user(Role.MANAGER, otherBuildingId)
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainForbiddenException> { service.getThread(ticket.id, otherManager) }
    }

    @Test
    fun `the manager's reply advances the ticket and notifies the resident`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket
        every { ticketRepository.save(any()) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }

        service.reply(ticket.id, textReply(), manager)

        assertEquals(TicketStatus.IN_PROGRESS, ticket.status)
        verify(exactly = 1) {
            notificationService.notifyUser(resident.id, any(), any(), any(), any())
        }
    }

    @Test
    fun `a resident replying to an answered ticket reopens it`() {
        val ticket = ticket()
        ticket.markAnswered()
        every { ticketRepository.findById(ticket.id) } returns ticket
        every { ticketRepository.save(any()) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }

        service.reply(ticket.id, textReply("هنوز حل نشده"), resident)

        assertEquals(TicketStatus.AWAITING_REPLY, ticket.status)
    }

    @Test
    fun `only the manager can mark a ticket answered`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainForbiddenException> { service.markAnswered(ticket.id, resident) }
        assertFailsWith<DomainForbiddenException> { service.markAnswered(ticket.id, staff) }
    }

    @Test
    fun `the manager marks a ticket answered and the resident is told`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket
        every { ticketRepository.save(any()) } answers { firstArg() }

        val answered = service.markAnswered(ticket.id, manager)

        assertTrue(answered.answered)
        verify(exactly = 1) { notificationService.notifyUser(resident.id, any(), any(), any(), any()) }
    }

    @Test
    fun `a text reply without a body is refused`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainValidationException> {
            service.reply(ticket.id, textReply().copy(body = null), resident)
        }
    }

    @Test
    fun `an oversized attachment is refused`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainValidationException> {
            service.uploadAttachment(
                ticket.id,
                TicketMessageKind.IMAGE,
                upload(sizeBytes = SupportTicketService.MAX_ATTACHMENT_BYTES + 1),
                resident,
            )
        }
    }

    @Test
    fun `an attachment of the wrong type for its kind is refused`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainValidationException> {
            service.uploadAttachment(
                ticket.id, TicketMessageKind.VOICE, upload(contentType = "image/png"), resident,
            )
        }
    }

    @Test
    fun `a voice note keeps its codec parameters`() {
        // Browsers send "audio/webm;codecs=opus"; rejecting that would make
        // every recorded voice note fail.
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket
        every { attachmentStorage.store(any(), any(), any(), any(), any()) } returns "support/key"

        val key = service.uploadAttachment(
            ticket.id, TicketMessageKind.VOICE, upload(contentType = "audio/webm;codecs=opus"), resident,
        )

        assertEquals("support/key", key)
    }

    @Test
    fun `staff cannot upload an attachment`() {
        val ticket = ticket()
        every { ticketRepository.findById(ticket.id) } returns ticket

        assertFailsWith<DomainForbiddenException> {
            service.uploadAttachment(ticket.id, TicketMessageKind.IMAGE, upload(), staff)
        }
    }

    @Test
    fun `staff have no ticket list of their own`() {
        assertFailsWith<DomainForbiddenException> { service.getMine(staff) }
        assertFailsWith<DomainForbiddenException> { service.getForBuilding(null, staff) }
    }

    private fun upload(
        contentType: String = "image/png",
        sizeBytes: Long = 1024,
    ) = TicketAttachmentUpload(
        originalFilename = "photo.png",
        contentType = contentType,
        sizeBytes = sizeBytes,
        content = "x".byteInputStream(),
    )
}
