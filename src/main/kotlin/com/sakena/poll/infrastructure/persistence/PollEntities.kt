package com.sakena.poll.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA persistence models for the poll context. */
@Entity
@Table(name = "polls")
class PollEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "building_id")
    var buildingId: UUID?,

    @Column(name = "question", nullable = false, length = 300)
    var question: String,

    @Column(name = "created_by", nullable = false, updatable = false)
    var createdBy: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "closed_at")
    var closedAt: Instant?,
)

@Entity
@Table(name = "poll_options")
class PollOptionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "poll_id", nullable = false, updatable = false)
    var pollId: UUID,

    @Column(name = "label", nullable = false, length = 200)
    var label: String,

    @Column(name = "position", nullable = false)
    var position: Int,
)

@Entity
@Table(name = "poll_votes")
class PollVoteEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "poll_id", nullable = false, updatable = false)
    var pollId: UUID,

    @Column(name = "option_id", nullable = false, updatable = false)
    var optionId: UUID,

    @Column(name = "voter_id", nullable = false, updatable = false)
    var voterId: UUID,

    @Column(name = "cast_at", nullable = false, updatable = false)
    var castAt: Instant,
)
