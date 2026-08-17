package com.sakena.chat.infrastructure.persistence

import com.sakena.chat.domain.ChatMessageRepository
import com.sakena.chat.domain.model.ChatMessage
import com.sakena.chat.domain.model.ChatMessageId
import com.sakena.property.domain.model.BuildingId
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Adapter implementing the domain [ChatMessageRepository] port on top of
 * Spring Data JPA. This is the only place that knows about
 * [ChatMessageEntity] and [ChatMessageJpaRepository].
 */
@Component
class ChatMessageRepositoryAdapter(
    private val jpaRepository: ChatMessageJpaRepository,
) : ChatMessageRepository {

    override fun save(message: ChatMessage): ChatMessage {
        val saved = jpaRepository.save(ChatMessageEntityMapper.toEntity(message))
        return ChatMessageEntityMapper.toDomain(saved)
    }

    override fun findById(id: ChatMessageId): ChatMessage? =
        jpaRepository.findByIdOrNull(id.value)?.let(ChatMessageEntityMapper::toDomain)

    override fun findPage(
        buildingId: BuildingId,
        before: java.time.Instant?,
        limit: Int,
    ): List<ChatMessage> {
        val page = PageRequest.of(0, limit)
        val entities = if (before == null) {
            jpaRepository.findByBuildingIdOrderBySentAtDesc(buildingId.value, page)
        } else {
            jpaRepository.findByBuildingIdAndSentAtLessThanOrderBySentAtDesc(buildingId.value, before, page)
        }
        // The query pages backwards from the newest; the client renders oldest-first.
        return entities.map(ChatMessageEntityMapper::toDomain).reversed()
    }

    override fun findSince(buildingId: BuildingId, since: java.time.Instant): List<ChatMessage> =
        jpaRepository.findAllByBuildingIdAndSentAtGreaterThanOrderBySentAt(buildingId.value, since)
            .map(ChatMessageEntityMapper::toDomain)
}
