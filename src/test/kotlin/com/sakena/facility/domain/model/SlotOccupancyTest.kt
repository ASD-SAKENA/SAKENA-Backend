package com.sakena.facility.domain.model

import com.sakena.user.domain.UserId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SlotOccupancyTest {

    private val facilityId = FacilityId(UUID.randomUUID())
    private val eight: Instant = Instant.parse("2026-09-01T08:00:00Z")

    private fun at(fromHour: Long, toHour: Long, people: Int): FacilityBooking =
        FacilityBooking.create(
            facilityId = facilityId,
            bookedBy = UserId(UUID.randomUUID()),
            startsAt = eight.plus(fromHour, ChronoUnit.HOURS),
            endsAt = eight.plus(toHour, ChronoUnit.HOURS),
            partySize = people,
        )

    @Test
    fun `an empty facility is unoccupied`() {
        assertEquals(0, SlotOccupancy.peakWithin(emptyList(), eight, eight.plus(2, ChronoUnit.HOURS)))
    }

    @Test
    fun `back-to-back bookings never share a moment`() {
        // 08-09 and 09-10 must not add up: the facility is never busier than 10.
        val bookings = listOf(at(0, 1, 10), at(1, 2, 10))

        assertEquals(10, SlotOccupancy.peakWithin(bookings, eight, eight.plus(2, ChronoUnit.HOURS)))
    }

    @Test
    fun `overlapping bookings add up at the moment they share`() {
        val bookings = listOf(at(0, 2, 4), at(1, 3, 6))

        assertEquals(10, SlotOccupancy.peakWithin(bookings, eight, eight.plus(3, ChronoUnit.HOURS)))
    }

    @Test
    fun `a booking outside the range is ignored`() {
        val bookings = listOf(at(0, 1, 10))

        assertEquals(
            0,
            SlotOccupancy.peakWithin(bookings, eight.plus(1, ChronoUnit.HOURS), eight.plus(2, ChronoUnit.HOURS)),
        )
    }

    @Test
    fun `a booking already running when the range opens still counts`() {
        // Starts before the window, so its start instant is not a boundary
        // inside it — clamping is what keeps it visible.
        val bookings = listOf(at(0, 3, 7))

        assertEquals(
            7,
            SlotOccupancy.peakWithin(bookings, eight.plus(1, ChronoUnit.HOURS), eight.plus(2, ChronoUnit.HOURS)),
        )
    }

    @Test
    fun `a cancelled booking occupies nothing`() {
        val cancelled = at(0, 2, 10).apply { cancel() }

        assertEquals(0, SlotOccupancy.peakWithin(listOf(cancelled), eight, eight.plus(2, ChronoUnit.HOURS)))
    }
}
