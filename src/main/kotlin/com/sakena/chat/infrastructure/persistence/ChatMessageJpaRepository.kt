package com.sakena.chat.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatMessageJpaRepository : JpaRepository<ChatMessageEntity, UUID> {

    @Query(
        """
        SELECT m FROM ChatMessageEntity m
        WHERE m.buildingId = :buildingId
          AND (:before IS NULL OR m.sentAt < :before)
        ORDER BY m.sentAt DESC
        """
    )
    fun findPage(
        @Param("buildingId") buildingId: UUID,
        @Param("before") before: Instant?,
        pageable: Pageable,
    ): List<ChatMessageEntity>

    fun findAllByBuildingIdAndSentAtGreaterThanOrderBySentAt(
        buildingId: UUID,
        sentAt: Instant,
    ): List<ChatMessageEntity>
}
