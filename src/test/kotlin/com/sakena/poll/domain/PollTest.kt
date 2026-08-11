package com.sakena.poll.domain

import com.sakena.poll.domain.model.Poll
import com.sakena.poll.domain.model.PollOptionId
import com.sakena.poll.domain.model.PollResults
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PollTest {

    private val manager = UserId.generate()

    private fun poll() = Poll.create("Replace the lobby carpet?", listOf(" Yes ", "No"), manager)

    @Test
    fun `create trims options and keeps their order`() {
        val poll = poll()

        assertEquals(listOf("Yes", "No"), poll.options.map { it.label })
        assertEquals(listOf(0, 1), poll.options.map { it.position })
        assertTrue(poll.open)
    }

    @Test
    fun `create rejects fewer than two options`() {
        assertFailsWith<DomainValidationException> {
            Poll.create("Only one?", listOf("Yes"), manager)
        }
    }

    @Test
    fun `create rejects duplicate options`() {
        assertFailsWith<DomainValidationException> {
            Poll.create("Twice?", listOf("Yes", "Yes"), manager)
        }
    }

    @Test
    fun `a closed poll no longer accepts votes`() {
        val poll = poll()
        val optionId = poll.options.first().id

        poll.close()

        assertFalse(poll.open)
        assertFailsWith<DomainConflictException> { poll.requireOpenFor(optionId) }
    }

    @Test
    fun `voting for an option of another poll is rejected`() {
        val poll = poll()

        assertFailsWith<DomainValidationException> { poll.requireOpenFor(PollOptionId.new()) }
    }

    @Test
    fun `results compute each option's percentage of the total`() {
        val poll = poll()
        val yes = poll.options[0].id
        val no = poll.options[1].id

        val results = PollResults.of(poll, mapOf(yes to 3L, no to 1L), myOptionId = yes)

        assertEquals(4L, results.totalVotes)
        assertEquals(75.0, results.options.first { it.optionId == yes }.percentage)
        assertEquals(25.0, results.options.first { it.optionId == no }.percentage)
        assertTrue(results.hasVoted)
    }

    @Test
    fun `results of a poll without votes are all zero`() {
        val poll = poll()

        val results = PollResults.of(poll, emptyMap(), myOptionId = null)

        assertEquals(0L, results.totalVotes)
        assertTrue(results.options.all { it.percentage == 0.0 })
        assertFalse(results.hasVoted)
    }
}
