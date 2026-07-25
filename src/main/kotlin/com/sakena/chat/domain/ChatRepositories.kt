package com.sakena.chat.domain

import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageId
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException
import java.time.Instant

/**
 * Outbound port for persisting chat messages. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage

    fun findById(id: ChatMessageId): ChatMessage?

    /**
     * The newest [limit] messages of a building, oldest-first for rendering.
     * When [before] is given, only messages sent strictly earlier are returned,
     * which is how the client pages backwards through history.
     */
    fun findPage(buildingId: BuildingId, before: Instant?, limit: Int): List<ChatMessage>

    /** Messages sent after [since], oldest-first — the client's polling tail. */
    fun findSince(buildingId: BuildingId, since: Instant): List<ChatMessage>
}

/**
 * Outbound port for the object storage holding chat attachments. Keeps MinIO
 * out of the domain and application layers.
 */
interface ChatAttachmentStorage {
    /** Stores the bytes and returns the storage key they were written under. */
    fun store(
        buildingId: BuildingId,
        originalFilename: String?,
        contentType: String,
        sizeBytes: Long,
        content: java.io.InputStream,
    ): String

    /** A short-lived URL the browser can use to fetch the object directly. */
    fun presignedUrl(storageKey: String): String

    fun delete(storageKey: String)
}

class ChatMessageNotFoundException(id: ChatMessageId) :
    EntityNotFoundException("Chat message with id '$id' was not found")
