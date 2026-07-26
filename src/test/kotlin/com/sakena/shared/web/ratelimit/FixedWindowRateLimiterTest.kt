package com.sakena.shared.web.ratelimit

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedWindowRateLimiterTest {

    private var now: Instant = Instant.parse("2026-07-26T10:00:00Z")

    private fun limiter(limit: Int = 3, window: Duration = Duration.ofSeconds(60)) =
        FixedWindowRateLimiter(limit, window) { now }

    @Test
    fun `allows attempts up to the limit and blocks the next one`() {
        val limiter = limiter()

        repeat(3) { assertTrue(limiter.tryAcquire("1.2.3.4")) }

        assertFalse(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `counts each caller separately`() {
        val limiter = limiter(limit = 1)

        assertTrue(limiter.tryAcquire("1.2.3.4"))
        assertFalse(limiter.tryAcquire("1.2.3.4"))
        assertTrue(limiter.tryAcquire("5.6.7.8"))
    }

    @Test
    fun `starts a fresh window once the old one expires`() {
        val limiter = limiter(limit = 1)
        assertTrue(limiter.tryAcquire("1.2.3.4"))
        assertFalse(limiter.tryAcquire("1.2.3.4"))

        now = now.plusSeconds(61)

        assertTrue(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `reset forgives a caller immediately`() {
        val limiter = limiter(limit = 1)
        limiter.tryAcquire("1.2.3.4")
        assertFalse(limiter.tryAcquire("1.2.3.4"))

        limiter.reset("1.2.3.4")

        assertTrue(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `reports the seconds left in the window`() {
        val limiter = limiter()
        limiter.tryAcquire("1.2.3.4")

        now = now.plusSeconds(20)

        assertEquals(40, limiter.retryAfterSeconds("1.2.3.4"))
        assertEquals(0, limiter.retryAfterSeconds("unknown-caller"))
    }

    @Test
    fun `rejects a nonsensical configuration`() {
        assertFailsWith<IllegalArgumentException> {
            FixedWindowRateLimiter(0, Duration.ofSeconds(60))
        }
        assertFailsWith<IllegalArgumentException> {
            FixedWindowRateLimiter(5, Duration.ZERO)
        }
    }
}
