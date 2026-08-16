package com.sakena.chat.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface ChatMessageJpaRepository : JpaRepository<ChatMessageEntity, UUID> {

    /**
     * The newest page, with no lower bound. Kept as its own query — folding
     * this into one query with `:before IS NULL OR m.sentAt < :before` makes
     * Postgres unable to infer a type for a null-valued `:before` parameter
     * bound only inside an `IS NULL` check ("could not determine data type
     * of parameter"), which is exactly the case on every first page load.
     */
    fun findByBuildingIdOrderBySentAtDesc(buildingId: UUID, pageable: Pageable): List<ChatMessageEntity>

    fun findByBuildingIdAndSentAtLessThanOrderBySentAtDesc(
        buildingId: UUID,
        before: Instant,
        pageable: Pageable,
    ): List<ChatMessageEntity>

    fun findAllByBuildingIdAndSentAtGreaterThanOrderBySentAt(
        buildingId: UUID,
        sentAt: Instant,
    ): List<ChatMessageEntity>
}
