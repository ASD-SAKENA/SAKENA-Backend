package com.sakena.poll.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PollJpaRepository : JpaRepository<PollEntity, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<PollEntity>
}

interface PollOptionJpaRepository : JpaRepository<PollOptionEntity, UUID> {
    fun findAllByPollIdOrderByPosition(pollId: UUID): List<PollOptionEntity>

    fun findAllByPollIdInOrderByPosition(pollIds: Collection<UUID>): List<PollOptionEntity>
}

interface PollVoteJpaRepository : JpaRepository<PollVoteEntity, UUID> {
    fun findByPollIdAndVoterId(pollId: UUID, voterId: UUID): PollVoteEntity?

    @Query(
        """
        SELECT v.optionId AS optionId, COUNT(v) AS total
        FROM PollVoteEntity v
        WHERE v.pollId = :pollId
        GROUP BY v.optionId
        """
    )
    fun countByOption(@Param("pollId") pollId: UUID): List<OptionTally>
}

/** Projection of the per-option vote count query. */
interface OptionTally {
    fun getOptionId(): UUID

    fun getTotal(): Long
}
