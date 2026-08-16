package com.sakena.poll.domain

import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollId
import com.sakena.poll.domain.model.PollOptionId
import com.sakena.poll.domain.model.PollVote
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.UserId

/**
 * Outbound ports for the poll context. Declared in the domain layer and
 * implemented by adapters in infrastructure.
 */
interface PollRepository {
    fun save(poll: Poll): Poll

    fun findById(id: PollId): Poll?

    /** One building's polls, newest first. */
    fun findAllByBuildingNewestFirst(buildingId: BuildingId): List<Poll>
}

interface PollVoteRepository {
    fun save(vote: PollVote): PollVote

    fun findByPollAndVoter(pollId: PollId, voterId: UserId): PollVote?

    fun delete(vote: PollVote)

    /** Vote counts per option for a poll. */
    fun countByOption(pollId: PollId): Map<PollOptionId, Long>
}

class PollNotFoundException(id: PollId) :
    EntityNotFoundException("Poll with id '$id' was not found")
