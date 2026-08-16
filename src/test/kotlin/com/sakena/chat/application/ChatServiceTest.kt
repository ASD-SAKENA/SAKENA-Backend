package com.sakena.chat.application

import com.sakena.chat.application.command.EditMessageCommand
import com.sakena.chat.application.command.SendAttachmentCommand
import com.sakena.chat.application.command.SendTextMessageCommand
import com.sakena.chat.domain.ChatAttachmentStorage
import com.sakena.chat.domain.ChatMessageRepository
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageKind
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
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
    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val service = ChatService(
        messageRepository,
        attachmentStorage,
        buildingRepository,
        residencyRepository,
        apartmentRepository,
    )

    private val buildingId = BuildingId.new()

    private fun user(role: Role) = User.register(
        username = "u${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        rawPassword = "password123",
        passwordEncoder = { it },
        role = role,
        managedBuildingId = if (role == Role.MANAGER) BuildingId.new() else null,
    )

    private val author = user(Role.RESIDENT)
    private val manager = user(Role.MANAGER)

    private fun givenBuildingExists() {
        every { buildingRepository.existsById(buildingId) } returns true
    }

    /** The resident lives in [buildingId] — every membership check should pass. */
    private fun givenResidentLivesHere(resident: User, inBuildingId: BuildingId = buildingId) {
        val apartmentId = ApartmentId.new()
        every { residencyRepository.findActiveByResident(resident.id) } returns Residency.start(
            apartmentId = apartmentId,
            residentId = resident.id,
            tenancy = TenancyType.OWNER_OCCUPIER,
        )
        every { apartmentRepository.findById(apartmentId) } returns Apartment.create(
            buildingId = inBuildingId,
            unitNumber = "1",
            floorNumber = 1,
            areaSquareMeters = java.math.BigDecimal.TEN,
            bedrooms = 1,
        )
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
        every { buildingRepository.existsById(buildingId) } returns false

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
        every { messageRepository.findById(message.id) } returns message
        every { messageRepository.save(any()) } answers { firstArg() }

        val edited = service.edit(message.id, EditMessageCommand("Second"), author)

        assertEquals("Second", edited.body)
        assertTrue(edited.edited)
    }

    @Test
    fun `a neighbour cannot edit someone else's message`() {
        val message = ChatMessage.text(buildingId, author.id, "First")
        val neighbour = user(Role.RESIDENT)
        every { messageRepository.findById(message.id) } returns message

        assertFailsWith<DomainConflictException> {
            service.edit(message.id, EditMessageCommand("Hijacked"), neighbour)
        }
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
        every { messageRepository.findById(message.id) } returns message
        every { messageRepository.save(any()) } answers { firstArg() }
        justRun { attachmentStorage.delete("chat/key.png") }

        val deleted = service.delete(message.id, manager)

        assertTrue(deleted.deleted)
        verify(exactly = 1) { attachmentStorage.delete("chat/key.png") }
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
        every {
            messageRepository.findPage(buildingId, null, ChatService.MAX_PAGE_SIZE)
        } returns emptyList()

        service.getPage(buildingId, before = null, limit = 5_000, viewer = manager)

        verify { messageRepository.findPage(buildingId, null, ChatService.MAX_PAGE_SIZE) }
    }

    @Test
    fun `a manager can read and post in any building's chat`() {
        givenBuildingExists()
        every { messageRepository.findPage(buildingId, null, ChatService.DEFAULT_PAGE_SIZE) } returns emptyList()

        service.getPage(buildingId, before = null, limit = null, viewer = manager)

        verify { messageRepository.findPage(buildingId, null, ChatService.DEFAULT_PAGE_SIZE) }
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
        every { residencyRepository.findActiveByResident(author.id) } returns null

        assertFailsWith<DomainForbiddenException> {
            service.getPage(buildingId, before = null, limit = null, viewer = author)
        }
    }
}
