package com.sakena.chat.domain

import com.sakena.chat.domain.model.ChatAttachment
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageKind
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessageTest {

    private val buildingId = BuildingId.new()
    private val author = UserId.generate()
    private val neighbour = UserId.generate()
    private val manager = UserId.generate()

    private fun text() = ChatMessage.text(buildingId, author, "  Hello neighbours  ")

    private fun voice() = ChatMessage.withAttachment(
        buildingId = buildingId,
        senderId = author,
        kind = ChatMessageKind.VOICE,
        attachment = ChatAttachment("chat/a/b.webm", "audio/webm", 2048, 7),
        caption = null,
    )

    @Test
    fun `a text message is trimmed and starts unedited`() {
        val message = text()

        assertEquals("Hello neighbours", message.body)
        assertFalse(message.edited)
        assertFalse(message.deleted)
    }

    @Test
    fun `blank text is rejected`() {
        assertFailsWith<DomainValidationException> { ChatMessage.text(buildingId, author, "   ") }
    }

    @Test
    fun `the author can edit their message and it is marked as edited`() {
        val message = text()

        message.editBody("Hello again", author)

        assertEquals("Hello again", message.body)
        assertTrue(message.edited)
    }

    @Test
    fun `a neighbour cannot edit someone else's message`() {
        val message = text()

        assertFailsWith<DomainConflictException> { message.editBody("Hijacked", neighbour) }
    }

    @Test
    fun `attachment messages cannot be edited`() {
        val message = voice()

        assertFailsWith<DomainConflictException> { message.editBody("New text", author) }
    }

    @Test
    fun `the author can delete their own message`() {
        val message = text()

        message.deleteByAuthor(author)

        assertTrue(message.deleted)
        assertEquals(author, message.deletedBy)
    }

    @Test
    fun `the manager can delete anyone's message`() {
        val message = text()

        message.deleteByManager(manager)

        assertTrue(message.deleted)
        assertEquals(manager, message.deletedBy)
    }

    @Test
    fun `a neighbour cannot delete someone else's message`() {
        val message = text()

        assertFailsWith<DomainConflictException> {
            message.deleteByAuthor(neighbour)
        }
    }

    @Test
    fun `a deleted message can neither be edited nor deleted again`() {
        val message = text()
        message.deleteByAuthor(author)

        assertFailsWith<DomainConflictException> { message.editBody("Back", author) }
        assertFailsWith<DomainConflictException> {
            message.deleteByManager(manager)
        }
    }

    @Test
    fun `an attachment message keeps its optional caption`() {
        val message = ChatMessage.withAttachment(
            buildingId = buildingId,
            senderId = author,
            kind = ChatMessageKind.IMAGE,
            attachment = ChatAttachment("chat/a/b.png", "image/png", 1024, null),
            caption = "  Lobby  ",
        )

        assertEquals("Lobby", message.body)
        assertEquals(ChatMessageKind.IMAGE, message.kind)
    }

    @Test
    fun `an attachment message cannot be of kind TEXT`() {
        assertFailsWith<DomainValidationException> {
            ChatMessage.withAttachment(
                buildingId = buildingId,
                senderId = author,
                kind = ChatMessageKind.TEXT,
                attachment = ChatAttachment("chat/a/b.png", "image/png", 1024, null),
                caption = null,
            )
        }
    }

    @Test
    fun `an empty attachment is rejected`() {
        assertFailsWith<DomainValidationException> {
            ChatAttachment("chat/a/b.png", "image/png", 0, null)
        }
    }

    @Test
    fun `a voice message has no caption when none was given`() {
        assertNull(voice().body)
    }
}
