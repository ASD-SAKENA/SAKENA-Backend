package com.sakena.poll.application

import com.sakena.poll.application.command.CastVoteCommand
import com.sakena.poll.application.command.CreatePollCommand
import com.sakena.poll.domain.PollRepository
import com.sakena.poll.domain.PollVoteRepository
import com.sakena.poll.domain.model.AlreadyVotedException
import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollVote
import com.sakena.residency.application.ResidencyService
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PollServiceTest {

    private val pollRepository = mockk<PollRepository>(relaxed = true)
    private val voteRepository = mockk<PollVoteRepository>(relaxed = true)
    private val residencyService = mockk<ResidencyService>()
    private val service = PollService(pollRepository, voteRepository, residencyService)

    private val manager = UserId.generate()
    private val resident = UserId.generate()

    init {
        every { residencyService.requireActiveResidency(resident) } returns
            Residency.start(ApartmentId.new(), resident, TenancyType.TENANT)
    }

    private fun poll() = Poll.create("Replace the carpet?", listOf("Yes", "No"), manager)

    @Test
    fun `create persists a poll for the manager`() {
        val saved = slot<Poll>()
        every { pollRepository.save(capture(saved)) } answers { saved.captured }

        val poll = service.create(CreatePollCommand("Replace the carpet?", listOf("Yes", "No")), manager)

        assertEquals("Replace the carpet?", poll.question)
        assertEquals(manager, poll.createdBy)
    }

    @Test
    fun `vote records the ballot and returns the live tally`() {
        val poll = poll()
        val yes = poll.options.first().id
        every { pollRepository.findById(poll.id) } returns poll
        every { voteRepository.findByPollAndVoter(poll.id, resident) } returns null andThen
            PollVote.cast(poll.id, yes, resident)
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
        every { voteRepository.findByPollAndVoter(poll.id, resident) } returns
            PollVote.cast(poll.id, yes, resident)

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

        assertFailsWith<DomainConflictException> {
            service.vote(poll.id, CastVoteCommand(yes), resident)
        }
    }

    @Test
    fun `a resident with no active residency cannot vote`() {
        val poll = poll()
        val yes = poll.options.first().id
        val outsider = UserId.generate()
        every { residencyService.requireActiveResidency(outsider) } throws
            DomainForbiddenException("You must be an active resident of a unit to do this")

        assertFailsWith<DomainForbiddenException> {
            service.vote(poll.id, CastVoteCommand(yes), outsider)
        }
        verify(exactly = 0) { voteRepository.save(any()) }
    }
}
