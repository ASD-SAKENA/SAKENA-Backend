package com.sakena.poll.application

import com.sakena.poll.application.command.CastVoteCommand
import com.sakena.poll.application.command.CreatePollCommand
import com.sakena.poll.domain.PollRepository
import com.sakena.poll.domain.PollVoteRepository
import com.sakena.poll.domain.model.AlreadyVotedException
import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollVote
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PollServiceTest {

    private val pollRepository = mockk<PollRepository>(relaxed = true)
    private val voteRepository = mockk<PollVoteRepository>(relaxed = true)
    private val buildingAccess = mockk<BuildingAccess>()
    private val service = PollService(pollRepository, voteRepository, buildingAccess)

    private val buildingId = BuildingId.new()
    private val manager = user(Role.MANAGER)
    private val resident = user(Role.RESIDENT)

    private fun poll() = Poll.create(
        "Replace the carpet?",
        listOf("Yes", "No"),
        manager.id,
        buildingId,
    )

    @Test
    fun `create persists a poll for the manager`() {
        val saved = slot<Poll>()
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { pollRepository.save(capture(saved)) } answers { saved.captured }

        val poll = service.create(CreatePollCommand("Replace the carpet?", listOf("Yes", "No")), manager)

        assertEquals("Replace the carpet?", poll.question)
        assertEquals(manager.id, poll.createdBy)
        assertEquals(buildingId, poll.buildingId)
    }

    @Test
    fun `vote records the ballot and returns the live tally`() {
        val poll = poll()
        val yes = poll.options.first().id
        every { pollRepository.findById(poll.id) } returns poll
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { voteRepository.findByPollAndVoter(poll.id, resident.id) } returns null andThen
            PollVote.cast(poll.id, yes, resident.id)
        every { voteRepository.countByOption(poll.id) } returns mapOf(yes to 1L)

        val results = service.vote(poll.id, CastVoteCommand(yes), resident)

        assertEquals(1L, results.totalVotes)
        assertTrue(results.hasVoted)
        verify(exactly = 1) { voteRepository.save(any()) }
    }

    @Test
    fun `a resident cannot vote twice`() {
        val poll = poll()
        val yes = poll.options.first().id
        every { pollRepository.findById(poll.id) } returns poll
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { voteRepository.findByPollAndVoter(poll.id, resident.id) } returns
            PollVote.cast(poll.id, yes, resident.id)

        assertFailsWith<AlreadyVotedException> {
            service.vote(poll.id, CastVoteCommand(yes), resident)
        }
        verify(exactly = 0) { voteRepository.save(any()) }
    }

    @Test
    fun `voting on a closed poll is rejected`() {
        val poll = poll()
        val yes = poll.options.first().id
        poll.close()
        every { pollRepository.findById(poll.id) } returns poll
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId

        assertFailsWith<DomainConflictException> {
            service.vote(poll.id, CastVoteCommand(yes), resident)
        }
    }

    @Test
    fun `manager cannot vote in a resident poll`() {
        val poll = poll()

        assertFailsWith<DomainForbiddenException> {
            service.vote(poll.id, CastVoteCommand(poll.options.first().id), manager)
        }

        verify(exactly = 0) { pollRepository.findById(any()) }
        verify(exactly = 0) { voteRepository.save(any()) }
    }

    @Test
    fun `staff can read polls only from the assigned building`() {
        val staff = user(Role.STAFF)
        val poll = poll()
        every { buildingAccess.staffBuildingId(staff.id) } returns buildingId
        every { pollRepository.findAllByBuildingNewestFirst(buildingId) } returns listOf(poll)
        every { voteRepository.countByOption(poll.id) } returns emptyMap()
        every { voteRepository.findByPollAndVoter(poll.id, staff.id) } returns null

        assertEquals(1, service.getAll(staff).size)
    }

    private fun user(role: Role): User {
        val id = UserId.generate()
        val now = Instant.now()
        return User.reconstitute(
            id,
            "user-${id.value}",
            "${id.value}@example.com",
            "hash",
            role,
            now,
            now,
            true,
        )
    }
}
