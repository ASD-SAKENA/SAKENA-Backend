package com.sakena.chat.application

import com.sakena.chat.application.command.EditMessageCommand
import com.sakena.chat.application.command.SendAttachmentCommand
import com.sakena.chat.application.command.SendTextMessageCommand
import com.sakena.chat.domain.ChatAttachmentStorage
import com.sakena.chat.domain.ChatMessageNotFoundException
import com.sakena.chat.domain.ChatMessageRepository
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageKind
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChatServiceTest {

    private val messageRepository = mockk<ChatMessageRepository>()
    private val attachmentStorage = mockk<ChatAttachmentStorage>()
    private val buildingRepository = mockk<BuildingRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val service = ChatService(
        messageRepository,
        attachmentStorage,
        buildingRepository,
        buildingAccess,
    )

    private val buildingId = BuildingId.new()

    private fun user(role: Role) = User.register(
        username = "u${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        rawPassword = "password123",
        passwordEncoder = { it },
        role = role,
    )

    private val author = user(Role.RESIDENT)
    private val manager = user(Role.MANAGER)

    private fun givenBuildingExists() {
        every { buildingRepository.findById(buildingId) } returns
            Building.create("Tower", "Main Street", manager.id)
    }

    private fun givenManagerManagesHere() {
        justRun { buildingAccess.requireManagerAccess(buildingId, manager.id) }
    }

    private fun givenStaffWorksHere(staff: User) {
        justRun { buildingAccess.requireStaffAccess(buildingId, staff.id) }
    }

    /** Configures whether the resident may access [buildingId]. */
    private fun givenResidentLivesHere(resident: User, inBuildingId: BuildingId = buildingId) {
        if (inBuildingId == buildingId) {
            justRun { buildingAccess.requireResidentAccess(buildingId, resident.id) }
        } else {
            every {
                buildingAccess.requireResidentAccess(buildingId, resident.id)
            } throws DomainForbiddenException("You are not a resident of this building")
        }
    }

    @Test
    fun `sendText persists the message for an existing building`() {
        givenBuildingExists()
        givenResidentLivesHere(author)
        val saved = slot<ChatMessage>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        val message = service.sendText(buildingId, SendTextMessageCommand("Hello"), author)

        assertEquals("Hello", message.body)
        assertEquals(author.id, message.senderId)
    }

    @Test
    fun `sending into an unknown building is rejected`() {
        every { buildingRepository.findById(buildingId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.sendText(buildingId, SendTextMessageCommand("Hello"), author)
        }
    }

    @Test
    fun `sendAttachment stores the object and keeps its storage key`() {
        givenBuildingExists()
        givenResidentLivesHere(author)
        every {
            attachmentStorage.store(buildingId, "note.webm", "audio/webm;codecs=opus", 2048, any())
        } returns "chat/key.webm"
        val saved = slot<ChatMessage>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        val message = service.sendAttachment(
            buildingId,
            SendAttachmentCommand(
                kind = ChatMessageKind.VOICE,
                originalFilename = "note.webm",
                contentType = "audio/webm;codecs=opus",
                sizeBytes = 2048,
                content = "voice".byteInputStream(),
                caption = null,
                durationSeconds = 5,
            ),
            author,
        )

        assertEquals("chat/key.webm", message.attachment?.storageKey)
        assertEquals(5, message.attachment?.durationSeconds)
    }

    @Test
    fun `an unsupported attachment type is rejected before touching storage`() {
        givenBuildingExists()
        givenResidentLivesHere(author)

        assertFailsWith<DomainValidationException> {
            service.sendAttachment(
                buildingId,
                SendAttachmentCommand(
                    kind = ChatMessageKind.IMAGE,
                    originalFilename = "virus.exe",
                    contentType = "application/octet-stream",
                    sizeBytes = 10,
                    content = "x".byteInputStream(),
                    caption = null,
                    durationSeconds = null,
                ),
                author,
            )
        }
        verify(exactly = 0) { attachmentStorage.store(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an oversized attachment is rejected`() {
        givenBuildingExists()
        givenResidentLivesHere(author)

        assertFailsWith<DomainValidationException> {
            service.sendAttachment(
                buildingId,
                SendAttachmentCommand(
                    kind = ChatMessageKind.IMAGE,
                    originalFilename = "huge.png",
                    contentType = "image/png",
                    sizeBytes = ChatService.MAX_ATTACHMENT_BYTES + 1,
                    content = "x".byteInputStream(),
                    caption = null,
                    durationSeconds = null,
                ),
                author,
            )
        }
    }

    @Test
    fun `edit delegates the author check to the aggregate`() {
        val message = ChatMessage.text(buildingId, author.id, "First")
        givenBuildingExists()
        givenResidentLivesHere(author)
        every { messageRepository.findById(message.id) } returns message
        every { messageRepository.save(any()) } answers { firstArg() }

        val edited = service.edit(buildingId, message.id, EditMessageCommand("Second"), author)

        assertEquals("Second", edited.body)
        assertTrue(edited.edited)
    }

    @Test
    fun `a neighbour cannot edit someone else's message`() {
        val message = ChatMessage.text(buildingId, author.id, "First")
        val neighbour = user(Role.RESIDENT)
        givenBuildingExists()
        givenResidentLivesHere(neighbour)
        every { messageRepository.findById(message.id) } returns message

        assertFailsWith<DomainConflictException> {
            service.edit(buildingId, message.id, EditMessageCommand("Hijacked"), neighbour)
        }
    }

    @Test
    fun `message must belong to the building in the request path`() {
        val message = ChatMessage.text(BuildingId.new(), author.id, "Elsewhere")
        givenBuildingExists()
        givenResidentLivesHere(author)
        every { messageRepository.findById(message.id) } returns message

        assertFailsWith<ChatMessageNotFoundException> {
            service.edit(buildingId, message.id, EditMessageCommand("Changed"), author)
        }
        verify(exactly = 0) { messageRepository.save(any()) }
    }

    @Test
    fun `the manager can delete any message and its stored object is removed`() {
        val message = ChatMessage.withAttachment(
            buildingId = buildingId,
            senderId = author.id,
            kind = ChatMessageKind.IMAGE,
            attachment = com.sakena.chat.domain.model.ChatAttachment(
                "chat/key.png",
                "image/png",
                1024,
                null,
            ),
            caption = null,
        )
        givenBuildingExists()
        givenManagerManagesHere()
        every { messageRepository.findById(message.id) } returns message
        every { messageRepository.save(any()) } answers { firstArg() }
        justRun { attachmentStorage.delete("chat/key.png") }

        val deleted = service.delete(buildingId, message.id, manager)

        assertTrue(deleted.deleted)
        verify(exactly = 1) { attachmentStorage.delete("chat/key.png") }
    }

    @Test
    fun `a manager cannot delete a message from another building`() {
        val message = ChatMessage.text(buildingId, author.id, "Protected")
        givenBuildingExists()
        every { messageRepository.findById(message.id) } returns message
        every {
            buildingAccess.requireManagerAccess(buildingId, manager.id)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.delete(buildingId, message.id, manager) }
        verify(exactly = 0) { messageRepository.findById(any()) }
        verify(exactly = 0) { messageRepository.save(any()) }
    }

    @Test
    fun `a deleted message exposes no attachment url`() {
        val message = ChatMessage.text(buildingId, author.id, "Bye")
        message.delete(author.id, requesterIsManager = false)

        assertEquals(null, service.attachmentUrl(message))
    }

    @Test
    fun `the page size is clamped to the maximum`() {
        givenBuildingExists()
        givenManagerManagesHere()
        every {
            messageRepository.findPage(buildingId, null, ChatService.MAX_PAGE_SIZE)
        } returns emptyList()

        service.getPage(buildingId, before = null, limit = 5_000, viewer = manager)

        verify { messageRepository.findPage(buildingId, null, ChatService.MAX_PAGE_SIZE) }
    }

    @Test
    fun `a manager can read the chat of their managed building`() {
        givenBuildingExists()
        givenManagerManagesHere()
        every { messageRepository.findPage(buildingId, null, ChatService.DEFAULT_PAGE_SIZE) } returns emptyList()

        service.getPage(buildingId, before = null, limit = null, viewer = manager)

        verify { messageRepository.findPage(buildingId, null, ChatService.DEFAULT_PAGE_SIZE) }
    }

    @Test
    fun `a manager cannot read another building's chat`() {
        givenBuildingExists()
        every {
            buildingAccess.requireManagerAccess(buildingId, manager.id)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.getPage(buildingId, before = null, limit = null, viewer = manager)
        }
        verify(exactly = 0) { messageRepository.findPage(any(), any(), any()) }
    }

    @Test
    fun `staff can read the chat of their assigned building`() {
        val staff = user(Role.STAFF)
        givenBuildingExists()
        givenStaffWorksHere(staff)
        every { messageRepository.findPage(buildingId, null, ChatService.DEFAULT_PAGE_SIZE) } returns emptyList()

        service.getPage(buildingId, before = null, limit = null, viewer = staff)

        verify { messageRepository.findPage(buildingId, null, ChatService.DEFAULT_PAGE_SIZE) }
    }

    @Test
    fun `staff cannot read another building's chat`() {
        val staff = user(Role.STAFF)
        givenBuildingExists()
        every {
            buildingAccess.requireStaffAccess(buildingId, staff.id)
        } throws DomainForbiddenException("You are not assigned to this building")

        assertFailsWith<DomainForbiddenException> {
            service.getPage(buildingId, before = null, limit = null, viewer = staff)
        }
        verify(exactly = 0) { messageRepository.findPage(any(), any(), any()) }
    }

    @Test
    fun `a resident of the building can send a message`() {
        givenBuildingExists()
        givenResidentLivesHere(author)
        val saved = slot<ChatMessage>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        val message = service.sendText(buildingId, SendTextMessageCommand("Hello"), author)

        assertEquals("Hello", message.body)
    }

    @Test
    fun `a resident of a different building cannot read its chat`() {
        val otherBuildingId = BuildingId.new()
        givenBuildingExists()
        givenResidentLivesHere(author, inBuildingId = otherBuildingId)

        assertFailsWith<DomainForbiddenException> {
            service.getPage(buildingId, before = null, limit = null, viewer = author)
        }
    }

    @Test
    fun `a resident of a different building cannot post to it`() {
        val otherBuildingId = BuildingId.new()
        givenBuildingExists()
        givenResidentLivesHere(author, inBuildingId = otherBuildingId)

        assertFailsWith<DomainForbiddenException> {
            service.sendText(buildingId, SendTextMessageCommand("Hello"), author)
        }
    }

    @Test
    fun `a resident with no active residency cannot reach any building's chat`() {
        givenBuildingExists()
        every {
            buildingAccess.requireResidentAccess(buildingId, author.id)
        } throws DomainForbiddenException("You are not a resident of any building")

        assertFailsWith<DomainForbiddenException> {
            service.getPage(buildingId, before = null, limit = null, viewer = author)
        }
    }
}
