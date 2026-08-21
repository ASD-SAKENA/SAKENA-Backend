package com.sakena.chat.infrastructure.web.dto

import com.sakena.chat.application.command.EditMessageCommand
import com.sakena.chat.application.command.SendTextMessageCommand
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SendMessageRequest(
    @field:NotBlank(message = "body must not be blank")
    @field:Size(max = 4000, message = "body must be at most 4000 characters")
    val body: String,
) {
    fun toCommand() = SendTextMessageCommand(body = body)
}

data class EditMessageRequest(
    @field:NotBlank(message = "body must not be blank")
    @field:Size(max = 4000, message = "body must be at most 4000 characters")
    val body: String,
) {
    fun toCommand() = EditMessageCommand(body = body)
}

/**
 * A message as the client renders it. Deleted messages keep their place in the
 * conversation as a tombstone with no body or attachment, and `editedAt` is
 * what the UI uses to show the "edited" marker.
 */
data class ChatMessageResponse(
    val id: UUID,
    val buildingId: UUID,
    val senderId: UUID,
    val senderName: String,
    /** Short-lived URL, or null when the sender has no picture. */
    val senderAvatarUrl: String?,
    val kind: ChatMessageKind,
    val body: String?,
    val attachmentUrl: String?,
    val attachmentContentType: String?,
    val attachmentSizeBytes: Long?,
    val attachmentDurationSeconds: Int?,
    val sentAt: Instant,
    val editedAt: Instant?,
    val edited: Boolean,
    val deleted: Boolean,
    val deletedAt: Instant?,
    val mine: Boolean,
) {
    companion object {
        fun from(
            message: ChatMessage,
            senderName: String,
            senderAvatarUrl: String?,
            attachmentUrl: String?,
            viewerIsSender: Boolean,
        ) = ChatMessageResponse(
            id = message.id.value,
            buildingId = message.buildingId.value,
            senderId = message.senderId.value,
            senderName = senderName,
            senderAvatarUrl = senderAvatarUrl,
            kind = message.kind,
            body = if (message.deleted) null else message.body,
            attachmentUrl = attachmentUrl,
            attachmentContentType = message.attachment?.contentType?.takeUnless { message.deleted },
            attachmentSizeBytes = message.attachment?.sizeBytes?.takeUnless { message.deleted },
            attachmentDurationSeconds =
                message.attachment?.durationSeconds?.takeUnless { message.deleted },
            sentAt = message.sentAt,
            editedAt = message.editedAt,
            edited = message.edited,
            deleted = message.deleted,
            deletedAt = message.deletedAt,
            mine = viewerIsSender,
        )
    }
}
