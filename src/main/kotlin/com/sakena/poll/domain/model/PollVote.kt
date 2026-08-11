package com.sakena.poll.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

/** Value object identifying a [PollVote]. */
@JvmInline
value class PollVoteId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): PollVoteId = PollVoteId(UUID.randomUUID())
    }
}

/**
 * PollVote aggregate — one resident's answer. Votes are immutable: the "one
 * vote per resident" rule is enforced by the unique (poll, voter) constraint
 * and checked by the application service before writing.
 */
class PollVote private constructor(
    val id: PollVoteId,
    val pollId: PollId,
    val optionId: PollOptionId,
    val voterId: UserId,
    val castAt: Instant,
) {
    companion object {
        fun cast(pollId: PollId, optionId: PollOptionId, voterId: UserId): PollVote =
            PollVote(PollVoteId.new(), pollId, optionId, voterId, Instant.now())

        /** Rebuilds from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: PollVoteId,
            pollId: PollId,
            optionId: PollOptionId,
            voterId: UserId,
            castAt: Instant,
        ): PollVote = PollVote(id, pollId, optionId, voterId, castAt)
    }
}

class NoVoteToWithdrawException(pollId: PollId) :
    DomainValidationException("You have not voted in poll '$pollId'")

/** Tally of one option, including its share of the total — used by the results view. */
data class PollOptionResult(
    val optionId: PollOptionId,
    val label: String,
    val votes: Long,
    val percentage: Double,
)

/**
 * Poll results snapshot handed to the web layer: the tallies plus whether the
 * asking resident has already voted (and for what).
 */
data class PollResults(
    val poll: Poll,
    val totalVotes: Long,
    val options: List<PollOptionResult>,
    val myOptionId: PollOptionId?,
) {
    val hasVoted: Boolean get() = myOptionId != null

    companion object {
        fun of(poll: Poll, votesByOption: Map<PollOptionId, Long>, myOptionId: PollOptionId?): PollResults {
            val total = votesByOption.values.sum()
            val options = poll.options
                .sortedBy { it.position }
                .map { option ->
                    val votes = votesByOption[option.id] ?: 0L
                    PollOptionResult(
                        optionId = option.id,
                        label = option.label,
                        votes = votes,
                        percentage = if (total == 0L) 0.0 else votes * 100.0 / total,
                    )
                }
            return PollResults(poll, total, options, myOptionId)
        }
    }
}

/** Thrown when a resident tries to vote twice on the same poll. */
class AlreadyVotedException(pollId: PollId) :
    DomainValidationException("You have already voted in poll '$pollId'")
