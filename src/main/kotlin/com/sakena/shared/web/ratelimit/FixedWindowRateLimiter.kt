package com.sakena.shared.web.ratelimit

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fixed-window counter keyed by caller (typically the client IP). Each key
 * may spend [limit] attempts per [window]; the window restarts on the first
 * attempt after it expires.
 *
 * In-memory on purpose: it protects a single instance from credential
 * stuffing and runaway clients. A cluster-wide limit belongs at the gateway.
 */
class FixedWindowRateLimiter(
    private val limit: Int,
    private val window: Duration,
    private val clock: () -> Instant = Instant::now,
) {
    init {
        require(limit > 0) { "limit must be positive" }
        require(!window.isZero && !window.isNegative) { "window must be positive" }
    }

    private class Counter(val startedAt: Instant) {
        val hits = AtomicInteger(0)
    }

    private val counters = ConcurrentHashMap<String, Counter>()

    /** Records an attempt; false means the caller is over the limit. */
    fun tryAcquire(key: String): Boolean {
        val now = clock()
        evictExpired(now)
        val counter = counters.compute(key) { _, existing ->
            if (existing == null || existing.startedAt.plus(window) <= now) Counter(now) else existing
        }!!
        return counter.hits.incrementAndGet() <= limit
    }

    /** Seconds until the caller's window resets — sent back as `Retry-After`. */
    fun retryAfterSeconds(key: String): Long {
        val counter = counters[key] ?: return 0
        val remaining = Duration.between(clock(), counter.startedAt.plus(window)).seconds
        return remaining.coerceAtLeast(1)
    }

    /** Forgets a caller — used once they authenticate successfully. */
    fun reset(key: String) {
        counters.remove(key)
    }

    /** Drops every window — used between integration tests so suites don't share limits. */
    fun clear() {
        counters.clear()
    }

    /**
     * Drops windows that have already expired, so a long-running instance does
     * not accumulate an entry per IP that ever hit the endpoint.
     */
    private fun evictExpired(now: Instant) {
        if (counters.size < EVICTION_THRESHOLD) return
        counters.entries.removeIf { it.value.startedAt.plus(window) <= now }
    }

    private companion object {
        const val EVICTION_THRESHOLD = 1_000
    }
}
