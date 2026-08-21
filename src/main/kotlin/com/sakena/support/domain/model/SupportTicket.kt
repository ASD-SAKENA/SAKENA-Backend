package com.sakena.support.domain.model

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

@JvmInline
value class TicketId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new() = TicketId(UUID.randomUUID())

        fun from(raw: String): TicketId = runCatching { TicketId(UUID.fromString(raw)) }
            .getOrElse { throw DomainValidationException("'$raw' is not a valid ticket id") }
    }
}

/** What the resident is raising, so a manager can triage the queue. */
enum class TicketCategory {
    COMPLAINT,
    CRITICISM,
    SUGGESTION,
}

/**
 * Where the conversation stands. The manager drives it forward by replying and
 * finally answering; a resident replying to an answered ticket reopens it.
 */
enum class TicketStatus {
    AWAITING_REPLY,
    IN_PROGRESS,
    ANSWERED,
}

/**
 * SupportTicket aggregate root — one private thread between a resident and
 * their building manager for a complaint, criticism or suggestion.
 *
 * [raisedBy] is always stored, even for an anonymous ticket: replies have to
 * reach someone and the resident must still see their own thread. Anonymity is
 * a presentation rule applied at the web boundary, never a gap in the record.
 */
class SupportTicket private constructor(
    val id: TicketId,
    val buildingId: BuildingId,
    val raisedBy: UserId,
    val category: TicketCategory,
    val subject: String,
    /** Hides the resident's identity from the manager; fixed at creation. */
    val anonymous: Boolean,
    status: TicketStatus,
    val createdAt: Instant,
    updatedAt: Instant,
    lastMessageAt: Instant,
) {
    var status: TicketStatus = status
        private set

    var updatedAt: Instant = updatedAt
        private set

    /** Drives the manager's queue ordering: the freshest conversation first. */
    var lastMessageAt: Instant = lastMessageAt
        private set

    val answered: Boolean get() = status == TicketStatus.ANSWERED

    /**
     * Moves the ticket along for a new message from [author].
     *
     * A manager's reply puts the ticket in progress; a resident replying to an
     * answered ticket reopens it, so a dissatisfied resident continues the same
     * thread instead of filing a duplicate.
     */
    fun recordReply(author: Role, now: Instant = Instant.now()) {
        status = when (author) {
            Role.MANAGER -> TicketStatus.IN_PROGRESS
            Role.RESIDENT -> TicketStatus.AWAITING_REPLY
            else -> throw DomainConflictException("Only a resident or a manager takes part in a support ticket")
        }
        lastMessageAt = now
        touch(now)
    }

    /** The manager declares the matter handled. */
    fun markAnswered(now: Instant = Instant.now()) {
        if (status == TicketStatus.ANSWERED) {
            throw DomainConflictException("This ticket is already marked as answered")
        }
        status = TicketStatus.ANSWERED
        touch(now)
    }

    private fun touch(now: Instant) {
        updatedAt = now
    }

    companion object {
        const val MAX_SUBJECT_LENGTH = 150

        fun open(
            buildingId: BuildingId,
            raisedBy: UserId,
            category: TicketCategory,
            subject: String,
            anonymous: Boolean,
            now: Instant = Instant.now(),
        ): SupportTicket = SupportTicket(
            id = TicketId.new(),
            buildingId = buildingId,
            raisedBy = raisedBy,
            category = category,
            subject = validateSubject(subject),
            anonymous = anonymous,
            status = TicketStatus.AWAITING_REPLY,
            createdAt = now,
            updatedAt = now,
            lastMessageAt = now,
        )

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        @Suppress("LongParameterList")
        fun reconstitute(
            id: TicketId,
            buildingId: BuildingId,
            raisedBy: UserId,
            category: TicketCategory,
            subject: String,
            anonymous: Boolean,
            status: TicketStatus,
            createdAt: Instant,
            updatedAt: Instant,
            lastMessageAt: Instant,
        ) = SupportTicket(
            id, buildingId, raisedBy, category, subject, anonymous,
            status, createdAt, updatedAt, lastMessageAt,
        )

        private fun validateSubject(subject: String): String {
            val trimmed = subject.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Ticket subject must not be blank")
            if (trimmed.length > MAX_SUBJECT_LENGTH) {
                throw DomainValidationException("Ticket subject must be at most $MAX_SUBJECT_LENGTH characters")
            }
            return trimmed
        }
    }
}
