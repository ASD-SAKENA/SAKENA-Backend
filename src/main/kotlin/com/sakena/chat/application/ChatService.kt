package com.sakena.chat.application

import com.sakena.chat.application.command.EditMessageCommand
import com.sakena.chat.application.command.SendAttachmentCommand
import com.sakena.chat.application.command.SendTextMessageCommand
import com.sakena.chat.domain.ChatAttachmentStorage
import com.sakena.chat.domain.ChatMessageNotFoundException
import com.sakena.chat.domain.ChatMessageRepository
import com.sakena.chat.domain.model.ChatAttachment
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageId
import com.sakena.chat.domain.model.ChatMessageKind
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Application service for a building's group chat. Owns the transaction
 * boundary and the attachment upload, and delegates every permission rule to
 * the [ChatMessage] aggregate.
 */
@Service
@Transactional
class ChatService(
    private val messageRepository: ChatMessageRepository,
    private val attachmentStorage: ChatAttachmentStorage,
    private val buildingRepository: BuildingRepository,
    private val buildingAccess: BuildingAccess,
) {

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024

        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        private val ALLOWED_AUDIO_TYPES =
            setOf("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav")
    }

    fun sendText(buildingId: BuildingId, command: SendTextMessageCommand, sender: User): ChatMessage {
        requireMembership(buildingId, sender)
        return messageRepository.save(ChatMessage.text(buildingId, sender.id, command.body))
    }

    fun sendAttachment(
        buildingId: BuildingId,
        command: SendAttachmentCommand,
        sender: User,
    ): ChatMessage {
        requireMembership(buildingId, sender)
        validateAttachment(command)

        val storageKey = attachmentStorage.store(
            buildingId = buildingId,
            originalFilename = command.originalFilename,
            contentType = command.contentType,
            sizeBytes = command.sizeBytes,
            content = command.content,
        )
        val message = ChatMessage.withAttachment(
            buildingId = buildingId,
            senderId = sender.id,
            kind = command.kind,
            attachment = ChatAttachment(
                storageKey = storageKey,
                contentType = command.contentType,
                sizeBytes = command.sizeBytes,
                durationSeconds = command.durationSeconds,
            ),
            caption = command.caption,
        )
        return messageRepository.save(message)
    }

    fun edit(
        buildingId: BuildingId,
        id: ChatMessageId,
        command: EditMessageCommand,
        editor: User,
    ): ChatMessage {
        requireMembership(buildingId, editor)
        val message = requireMessageInBuilding(id, buildingId)
        message.editBody(command.body, editor.id)
        return messageRepository.save(message)
    }

    /**
     * Soft-deletes the message. The stored object is removed too, since a
     * deleted attachment must not stay reachable through a presigned URL.
     */
    fun delete(buildingId: BuildingId, id: ChatMessageId, requester: User): ChatMessage {
        requireMembership(buildingId, requester)
        val message = requireMessageInBuilding(id, buildingId)
        message.delete(requester.id, requester.role == Role.MANAGER)
        val deleted = messageRepository.save(message)
        message.attachment?.let { attachmentStorage.delete(it.storageKey) }
        return deleted
    }

    @Transactional(readOnly = true)
    fun getPage(buildingId: BuildingId, before: Instant?, limit: Int?, viewer: User): List<ChatMessage> {
        requireMembership(buildingId, viewer)
        val size = (limit ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        return messageRepository.findPage(buildingId, before, size)
    }

    @Transactional(readOnly = true)
    fun getSince(buildingId: BuildingId, since: Instant, viewer: User): List<ChatMessage> {
        requireMembership(buildingId, viewer)
        return messageRepository.findSince(buildingId, since)
    }

    /** Attachments are private in storage, so the client gets a short-lived URL. */
    fun attachmentUrl(message: ChatMessage): String? =
        message.attachment
            ?.takeUnless { message.deleted }
            ?.let { attachmentStorage.presignedUrl(it.storageKey) }

    private fun validateAttachment(command: SendAttachmentCommand) {
        if (command.sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw DomainValidationException("Attachment must be at most 15 MB")
        }
        val allowed = when (command.kind) {
            ChatMessageKind.IMAGE -> ALLOWED_IMAGE_TYPES
            ChatMessageKind.VOICE -> ALLOWED_AUDIO_TYPES
            ChatMessageKind.TEXT -> throw DomainValidationException("Attachment kind must be IMAGE or VOICE")
        }
        // The browser sometimes appends codec parameters, e.g. "audio/webm;codecs=opus".
        val baseType = command.contentType.substringBefore(';').trim().lowercase()
        if (baseType !in allowed) {
            throw DomainValidationException("Unsupported attachment type '$baseType'")
        }
    }

    /**
     * Every role may only reach the building assigned through its own trusted
     * ownership or membership relation.
     */
    private fun requireMembership(buildingId: BuildingId, user: User) {
        if (buildingRepository.findById(buildingId) == null) {
            throw EntityNotFoundException("Building with id '$buildingId' was not found")
        }
        when (user.role) {
            Role.MANAGER -> buildingAccess.requireManagerAccess(buildingId, user.id)
            Role.RESIDENT -> buildingAccess.requireResidentAccess(buildingId, user.id)
            Role.STAFF -> buildingAccess.requireStaffAccess(buildingId, user.id)
        }
    }

    private fun requireMessage(id: ChatMessageId): ChatMessage =
        messageRepository.findById(id) ?: throw ChatMessageNotFoundException(id)

    private fun requireMessageInBuilding(id: ChatMessageId, buildingId: BuildingId): ChatMessage =
        requireMessage(id).takeIf { it.buildingId == buildingId }
            ?: throw ChatMessageNotFoundException(id)
}
