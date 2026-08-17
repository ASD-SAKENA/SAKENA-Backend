package com.sakena.poll.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

/** Value object identifying a [Poll] aggregate. */
@JvmInline
value class PollId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): PollId = PollId(UUID.randomUUID())

        fun from(raw: String): PollId =
            try {
                PollId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid poll id")
            }
    }
}

/** Value object identifying a [PollOption]. */
@JvmInline
value class PollOptionId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): PollOptionId = PollOptionId(UUID.randomUUID())

        fun from(raw: String): PollOptionId =
            try {
                PollOptionId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid poll option id")
            }
    }
}

/** One answer residents can pick, owned by its [Poll]. */
class PollOption private constructor(
    val id: PollOptionId,
    val label: String,
    val position: Int,
) {
    companion object {
        const val MAX_LABEL_LENGTH = 200

        fun create(label: String, position: Int): PollOption =
            PollOption(PollOptionId.new(), validateLabel(label), position)

        /** Rebuilds from already-persisted state. No invariants are re-checked. */
        fun reconstitute(id: PollOptionId, label: String, position: Int): PollOption =
            PollOption(id, label, position)

        private fun validateLabel(label: String): String {
            val trimmed = label.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Poll option must not be blank")
            if (trimmed.length > MAX_LABEL_LENGTH) {
                throw DomainValidationException("Poll option must be at most $MAX_LABEL_LENGTH characters")
            }
            return trimmed
        }
    }
}

/**
 * Poll aggregate root — a question the manager puts to the residents, with a
 * fixed set of options. Options are frozen at creation so that recorded votes
 * can never point at an option that changed meaning underneath them.
 */
class Poll private constructor(
    val id: PollId,
    val buildingId: BuildingId?,
    val question: String,
    val options: List<PollOption>,
    val createdBy: UserId,
    val createdAt: Instant,
    closedAt: Instant?,
) {
    var closedAt: Instant? = closedAt
        private set

    val open: Boolean get() = closedAt == null

    fun close() {
        if (!open) throw DomainConflictException("Poll is already closed")
        closedAt = Instant.now()
    }

    /** Guards vote recording — the application service checks this before writing. */
    fun requireOpenFor(optionId: PollOptionId) {
        if (!open) throw DomainConflictException("This poll is closed and no longer accepts votes")
        if (options.none { it.id == optionId }) {
            throw DomainValidationException("The chosen option does not belong to this poll")
        }
    }

    companion object {
        const val MAX_QUESTION_LENGTH = 300
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 10

        fun create(
            question: String,
            optionLabels: List<String>,
            createdBy: UserId,
            buildingId: BuildingId,
        ): Poll {
            val options = validateOptions(optionLabels)
            return Poll(
                id = PollId.new(),
                buildingId = buildingId,
                question = validateQuestion(question),
                options = options,
                createdBy = createdBy,
                createdAt = Instant.now(),
                closedAt = null,
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: PollId,
            buildingId: BuildingId?,
            question: String,
            options: List<PollOption>,
            createdBy: UserId,
            createdAt: Instant,
            closedAt: Instant?,
        ): Poll = Poll(id, buildingId, question, options, createdBy, createdAt, closedAt)

        private fun validateQuestion(question: String): String {
            val trimmed = question.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Poll question must not be blank")
            if (trimmed.length > MAX_QUESTION_LENGTH) {
                throw DomainValidationException("Poll question must be at most $MAX_QUESTION_LENGTH characters")
            }
            return trimmed
        }

        private fun validateOptions(labels: List<String>): List<PollOption> {
            val cleaned = labels.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.size < MIN_OPTIONS) {
                throw DomainValidationException("A poll needs at least $MIN_OPTIONS options")
            }
            if (cleaned.size > MAX_OPTIONS) {
                throw DomainValidationException("A poll can have at most $MAX_OPTIONS options")
            }
            if (cleaned.distinct().size != cleaned.size) {
                throw DomainValidationException("Poll options must be distinct")
            }
            return cleaned.mapIndexed { index, label -> PollOption.create(label, index) }
        }
    }
}
