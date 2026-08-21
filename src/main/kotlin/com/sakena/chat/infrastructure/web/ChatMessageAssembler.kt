package com.sakena.chat.infrastructure.web

import com.sakena.chat.application.ChatService
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.infrastructure.web.dto.ChatMessageResponse
import com.sakena.user.application.UserDirectory
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Component

/**
 * Builds chat responses: resolves sender names in one lookup per page (never
 * per message) and attaches a presigned URL for every surviving attachment.
 */
@Component
class ChatMessageAssembler(
    private val chatService: ChatService,
    private val userDirectory: UserDirectory,
) {

    fun toResponses(messages: List<ChatMessage>, viewerId: UserId): List<ChatMessageResponse> {
        if (messages.isEmpty()) return emptyList()
        val senderIds = messages.map { it.senderId }.toSet()
        val names = userDirectory.usernamesByIds(senderIds)
        // Resolved in one pass for the whole page rather than per message.
        val avatars = userDirectory.avatarUrlsByIds(senderIds)
        return messages.map { message ->
            ChatMessageResponse.from(
                message = message,
                senderName = names[message.senderId] ?: "کاربر حذف‌شده",
                senderAvatarUrl = avatars[message.senderId],
                attachmentUrl = chatService.attachmentUrl(message),
                viewerIsSender = message.senderId == viewerId,
            )
        }
    }

    fun toResponse(message: ChatMessage, viewerId: UserId): ChatMessageResponse =
        toResponses(listOf(message), viewerId).first()
}
