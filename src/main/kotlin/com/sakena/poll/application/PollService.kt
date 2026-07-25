package com.sakena.poll.application

import com.sakena.poll.application.command.CastVoteCommand
import com.sakena.poll.application.command.CreatePollCommand
import com.sakena.poll.domain.PollNotFoundException
import com.sakena.poll.domain.PollRepository
import com.sakena.poll.domain.PollVoteRepository
import com.sakena.poll.domain.model.AlreadyVotedException
import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollId
import com.sakena.poll.domain.model.PollResults
import com.sakena.poll.domain.model.PollVote
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service for the poll use cases: the manager creates and closes
 * polls, residents cast a single vote each and immediately see the tally.
 */
@Service
@Transactional
class PollService(
    private val pollRepository: PollRepository,
    private val voteRepository: PollVoteRepository,
) {

    fun create(command: CreatePollCommand, createdBy: UserId): Poll {
        val poll = Poll.create(command.question, command.options, createdBy)
        return pollRepository.save(poll)
    }

    fun close(id: PollId): Poll {
        val poll = requirePoll(id)
        poll.close()
        return pollRepository.save(poll)
    }

    /** Records the vote and returns the tally the voter should see right away. */
    fun vote(id: PollId, command: CastVoteCommand, voterId: UserId): PollResults {
        val poll = requirePoll(id)
        poll.requireOpenFor(command.optionId)
        if (voteRepository.findByPollAndVoter(id, voterId) != null) {
            throw AlreadyVotedException(id)
        }
        voteRepository.save(PollVote.cast(id, command.optionId, voterId))
        return resultsOf(poll, voterId)
    }

    @Transactional(readOnly = true)
    fun getResults(id: PollId, viewerId: UserId): PollResults = resultsOf(requirePoll(id), viewerId)

    @Transactional(readOnly = true)
    fun getAll(viewerId: UserId): List<PollResults> =
        pollRepository.findAllNewestFirst().map { resultsOf(it, viewerId) }

    private fun resultsOf(poll: Poll, viewerId: UserId): PollResults =
        PollResults.of(
            poll = poll,
            votesByOption = voteRepository.countByOption(poll.id),
            myOptionId = voteRepository.findByPollAndVoter(poll.id, viewerId)?.optionId,
        )

    private fun requirePoll(id: PollId): Poll =
        pollRepository.findById(id) ?: throw PollNotFoundException(id)
}
