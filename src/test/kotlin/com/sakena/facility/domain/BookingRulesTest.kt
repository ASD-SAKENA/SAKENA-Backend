package com.sakena.facility.domain

import com.sakena.facility.domain.model.BookingRules
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BookingRulesTest {

    private val zone = ZoneId.of("Asia/Tehran")

    /** A fixed Wednesday 09:00 local, used as "now" for every case. */
    private val now: Instant = Instant.parse("2026-08-05T05:30:00Z")

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        now.atZone(zone)
            .plusDays(day.toLong())
            .withHour(hour)
            .withMinute(minute)
            .truncatedTo(ChronoUnit.MINUTES)
            .toInstant()

    @Test
    fun `rejects closing before opening`() {
        assertFailsWith<DomainValidationException> {
            BookingRules.DEFAULT.copy(opensAt = LocalTime.of(22, 0), closesAt = LocalTime.of(8, 0))
        }
    }

    @Test
    fun `rejects a maximum shorter than the minimum`() {
        assertFailsWith<DomainValidationException> {
            BookingRules.DEFAULT.copy(minDurationMinutes = 120, maxDurationMinutes = 60)
        }
    }

    @Test
    fun `rejects a negative price and being closed every day`() {
        assertFailsWith<DomainValidationException> {
            BookingRules.DEFAULT.copy(hourlyPrice = BigDecimal("-1"))
        }
        assertFailsWith<DomainValidationException> {
            BookingRules.DEFAULT.copy(closedDays = DayOfWeek.entries.toSet())
        }
    }

    @Test
    fun `accepts a slot inside the opening hours`() {
        BookingRules.DEFAULT.validateSlot(at(1, 10), at(1, 11), zone, now)
    }

    @Test
    fun `rejects a slot in the past and one too far ahead`() {
        assertFailsWith<DomainConflictException> {
            BookingRules.DEFAULT.validateSlot(at(-1, 10), at(-1, 11), zone, now)
        }
        assertFailsWith<DomainConflictException> {
            BookingRules.DEFAULT.validateSlot(at(60, 10), at(60, 11), zone, now)
        }
    }

    @Test
    fun `rejects a slot shorter than the minimum or longer than the maximum`() {
        assertFailsWith<DomainConflictException> {
            BookingRules.DEFAULT.validateSlot(at(1, 10), at(1, 10, 15), zone, now)
        }
        assertFailsWith<DomainConflictException> {
            BookingRules.DEFAULT.validateSlot(at(1, 10), at(1, 14), zone, now)
        }
    }

    @Test
    fun `rejects a slot before opening or after closing`() {
        assertFailsWith<DomainConflictException> {
            BookingRules.DEFAULT.validateSlot(at(1, 6), at(1, 7), zone, now)
        }
        assertFailsWith<DomainConflictException> {
            BookingRules.DEFAULT.validateSlot(at(1, 21, 30), at(1, 22, 30), zone, now)
        }
    }

    @Test
    fun `rejects a slot on a closed day`() {
        val slot = at(1, 10)
        val closed = BookingRules.DEFAULT.copy(closedDays = setOf(slot.atZone(zone).dayOfWeek))

        assertFailsWith<DomainConflictException> {
            closed.validateSlot(slot, at(1, 11), zone, now)
        }
    }

    @Test
    fun `rejects an end that is not after the start`() {
        assertFailsWith<DomainValidationException> {
            BookingRules.DEFAULT.validateSlot(at(1, 10), at(1, 10), zone, now)
        }
    }

    @Test
    fun `prices a slot by the hour and rounds to whole units`() {
        val paid = BookingRules.DEFAULT.copy(hourlyPrice = BigDecimal("60000"))

        assertEquals(BigDecimal("60000"), paid.priceFor(at(1, 10), at(1, 11)))
        assertEquals(BigDecimal("30000"), paid.priceFor(at(1, 10), at(1, 10, 30)))
        assertEquals(BigDecimal.ZERO, BookingRules.DEFAULT.priceFor(at(1, 10), at(1, 11)))
    }

    @Test
    fun `treats a zero weekly limit as unlimited`() {
        assertTrue(BookingRules.DEFAULT.unlimitedPerWeek)
        assertTrue(!BookingRules.DEFAULT.copy(maxPerResidentPerWeek = 2).unlimitedPerWeek)
    }
}
