package com.sakena.chat.infrastructure.persistence

import com.sakena.chat.domain.model.ChatAttachment
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageId
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId

/** Translates between the domain aggregate and its JPA representation. */
internal object ChatMessageEntityMapper {

    fun toEntity(message: ChatMessage): ChatMessageEntity =
        ChatMessageEntity(
            id = message.id.value,
            buildingId = message.buildingId.value,
            senderId = message.senderId.value,
            kind = message.kind,
            body = message.body,
            attachmentKey = message.attachment?.storageKey,
            attachmentContentType = message.attachment?.contentType,
            attachmentSizeBytes = message.attachment?.sizeBytes,
            attachmentDurationSeconds = message.attachment?.durationSeconds,
            sentAt = message.sentAt,
            editedAt = message.editedAt,
            deletedAt = message.deletedAt,
            deletedBy = message.deletedBy?.value,
        )

    fun toDomain(entity: ChatMessageEntity): ChatMessage =
        ChatMessage.reconstitute(
            id = ChatMessageId(entity.id),
            buildingId = BuildingId(entity.buildingId),
            senderId = UserId(entity.senderId),
            kind = entity.kind,
            body = entity.body,
            attachment = entity.attachmentKey?.let { key ->
                ChatAttachment(
                    storageKey = key,
                    contentType = entity.attachmentContentType.orEmpty(),
                    sizeBytes = entity.attachmentSizeBytes ?: 0,
                    durationSeconds = entity.attachmentDurationSeconds,
                )
            },
            sentAt = entity.sentAt,
            editedAt = entity.editedAt,
            deletedAt = entity.deletedAt,
            deletedBy = entity.deletedBy?.let(::UserId),
        )
}
