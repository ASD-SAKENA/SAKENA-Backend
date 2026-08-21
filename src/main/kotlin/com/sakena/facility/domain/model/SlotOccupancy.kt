package com.sakena.facility.domain.model

import java.time.Instant

/**
 * Capacity is a constraint on a moment, not on a range.
 *
 * Summing the party sizes of every booking that touches a range overstates
 * occupancy: two bookings of 10 people at 08:00–09:00 and 09:00–10:00 never
 * share a moment, yet a naive sum reads 20 and locks a 10-person facility
 * out of the whole morning. This sweeps the boundaries instead and reports
 * the busiest instant, which is the only number capacity can be compared to.
 */
object SlotOccupancy {

    /** The most people present at any single instant inside [startsAt, endsAt). */
    fun peakWithin(
        bookings: List<FacilityBooking>,
        startsAt: Instant,
        endsAt: Instant,
    ): Int {
        val overlapping = bookings.filter {
            !it.cancelled && it.startsAt < endsAt && it.endsAt > startsAt
        }
        if (overlapping.isEmpty()) return 0

        // Occupancy only changes where a booking begins, so testing those
        // instants (clamped into the range) finds the maximum exactly.
        val boundaries = overlapping
            .map { if (it.startsAt.isBefore(startsAt)) startsAt else it.startsAt }
            .distinct()

        return boundaries.maxOf { instant ->
            overlapping
                .filter { it.startsAt <= instant && it.endsAt > instant }
                .sumOf { it.partySize }
        }
    }
}
