package com.sakena.poll.application

import com.sakena.poll.application.command.CastVoteCommand
import com.sakena.poll.application.command.CreatePollCommand
import com.sakena.poll.domain.PollNotFoundException
import com.sakena.poll.domain.PollRepository
import com.sakena.poll.domain.PollVoteRepository
import com.sakena.poll.domain.model.AlreadyVotedException
import com.sakena.poll.domain.model.NoVoteToWithdrawException
import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollId
import com.sakena.poll.domain.model.PollResults
import com.sakena.poll.domain.model.PollVote
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
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
    private val buildingAccess: BuildingAccess,
) {

    fun create(command: CreatePollCommand, createdBy: User): Poll {
        if (createdBy.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can create polls")
        }
        val poll = Poll.create(
            command.question,
            command.options,
            createdBy.id,
            buildingAccess.managedBuildingId(createdBy.id),
        )
        return pollRepository.save(poll)
    }

    fun close(id: PollId, manager: User): Poll {
        if (manager.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can close polls")
        }
        val poll = requireAccessiblePoll(id, manager)
        poll.close()
        return pollRepository.save(poll)
    }

    /** Records the vote and returns the tally the voter should see right away. */
    fun vote(id: PollId, command: CastVoteCommand, voter: User): PollResults {
        requireResident(voter)
        val poll = requireAccessiblePoll(id, voter)
        poll.requireOpenFor(command.optionId)
        if (voteRepository.findByPollAndVoter(id, voter.id) != null) {
            throw AlreadyVotedException(id)
        }
        voteRepository.save(PollVote.cast(id, command.optionId, voter.id))
        return resultsOf(poll, voter.id)
    }

    fun withdrawVote(id: PollId, voter: User): PollResults {
        requireResident(voter)
        val poll = requireAccessiblePoll(id, voter)
        if (!poll.open) {
            throw DomainConflictException("Poll is closed and votes may not be changed")
        }
        val vote = voteRepository.findByPollAndVoter(id, voter.id)
            ?: throw NoVoteToWithdrawException(id)
        voteRepository.delete(vote)
        return resultsOf(poll, voter.id)
    }

    @Transactional(readOnly = true)
    fun getResults(id: PollId, viewer: User): PollResults =
        resultsOf(requireAccessiblePoll(id, viewer), viewer.id)

    @Transactional(readOnly = true)
    fun getAll(viewer: User): List<PollResults> =
        pollRepository.findAllByBuildingNewestFirst(buildingIdFor(viewer))
            .map { resultsOf(it, viewer.id) }

    private fun resultsOf(poll: Poll, viewerId: UserId): PollResults =
        PollResults.of(
            poll = poll,
            votesByOption = voteRepository.countByOption(poll.id),
            myOptionId = voteRepository.findByPollAndVoter(poll.id, viewerId)?.optionId,
        )

    private fun requirePoll(id: PollId): Poll =
        pollRepository.findById(id) ?: throw PollNotFoundException(id)

    private fun requireAccessiblePoll(id: PollId, user: User): Poll {
        val poll = requirePoll(id)
        if (poll.buildingId == null || poll.buildingId != buildingIdFor(user)) {
            throw DomainForbiddenException("You cannot access this poll")
        }
        return poll
    }

    private fun buildingIdFor(user: User): BuildingId = when (user.role) {
        Role.MANAGER -> buildingAccess.managedBuildingId(user.id)
        Role.RESIDENT -> buildingAccess.residentBuildingId(user.id)
        Role.STAFF -> buildingAccess.staffBuildingId(user.id)
    }

    private fun requireResident(user: User) {
        if (user.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can vote in polls")
        }
    }
}
