package com.sakena.poll.infrastructure.persistence

import com.sakena.poll.domain.PollRepository
import com.sakena.poll.domain.PollVoteRepository
import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollId
import com.sakena.poll.domain.model.PollOption
import com.sakena.poll.domain.model.PollOptionId
import com.sakena.poll.domain.model.PollVote
import com.sakena.poll.domain.model.PollVoteId
import com.sakena.user.domain.UserId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Adapter implementing the domain [PollRepository] port. A poll's options are
 * part of the aggregate, so they are loaded and stored together with it.
 */
@Component
class PollRepositoryAdapter(
    private val pollJpaRepository: PollJpaRepository,
    private val optionJpaRepository: PollOptionJpaRepository,
) : PollRepository {

    override fun save(poll: Poll): Poll {
        pollJpaRepository.save(
            PollEntity(
                id = poll.id.value,
                question = poll.question,
                createdBy = poll.createdBy.value,
                createdAt = poll.createdAt,
                closedAt = poll.closedAt,
            ),
        )
        // Options never change after creation, so they are written once.
        if (optionJpaRepository.findAllByPollIdOrderByPosition(poll.id.value).isEmpty()) {
            optionJpaRepository.saveAll(
                poll.options.map {
                    PollOptionEntity(
                        id = it.id.value,
                        pollId = poll.id.value,
                        label = it.label,
                        position = it.position,
                    )
                },
            )
        }
        return poll
    }

    override fun findById(id: PollId): Poll? {
        val entity = pollJpaRepository.findByIdOrNull(id.value) ?: return null
        val options = optionJpaRepository.findAllByPollIdOrderByPosition(id.value)
        return toDomain(entity, options)
    }

    override fun findAllNewestFirst(): List<Poll> {
        val polls = pollJpaRepository.findAllByOrderByCreatedAtDesc()
        if (polls.isEmpty()) return emptyList()
        val optionsByPoll = optionJpaRepository
            .findAllByPollIdInOrderByPosition(polls.map { it.id })
            .groupBy { it.pollId }
        return polls.map { toDomain(it, optionsByPoll[it.id].orEmpty()) }
    }

    private fun toDomain(entity: PollEntity, options: List<PollOptionEntity>): Poll =
        Poll.reconstitute(
            id = PollId(entity.id),
            question = entity.question,
            options = options.map {
                PollOption.reconstitute(PollOptionId(it.id), it.label, it.position)
            },
            createdBy = UserId(entity.createdBy),
            createdAt = entity.createdAt,
            closedAt = entity.closedAt,
        )
}

/** Adapter implementing the domain [PollVoteRepository] port. */
@Component
class PollVoteRepositoryAdapter(
    private val jpaRepository: PollVoteJpaRepository,
) : PollVoteRepository {

    override fun save(vote: PollVote): PollVote {
        jpaRepository.save(
            PollVoteEntity(
                id = vote.id.value,
                pollId = vote.pollId.value,
                optionId = vote.optionId.value,
                voterId = vote.voterId.value,
                castAt = vote.castAt,
            ),
        )
        return vote
    }

    override fun findByPollAndVoter(pollId: PollId, voterId: UserId): PollVote? =
        jpaRepository.findByPollIdAndVoterId(pollId.value, voterId.value)?.let {
            PollVote.reconstitute(
                id = PollVoteId(it.id),
                pollId = PollId(it.pollId),
                optionId = PollOptionId(it.optionId),
                voterId = UserId(it.voterId),
                castAt = it.castAt,
            )
        }

    override fun countByOption(pollId: PollId): Map<PollOptionId, Long> =
        jpaRepository.countByOption(pollId.value)
            .associate { PollOptionId(it.getOptionId()) to it.getTotal() }
}
