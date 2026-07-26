package com.sakena.facility.domain

import com.sakena.facility.domain.model.BookingId
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.facility.domain.model.FacilityId
import com.sakena.user.domain.UserId
import java.time.Instant

/**
 * Outbound port for persisting facility bookings. Declared in the domain
 * layer and implemented by an adapter in infrastructure.
 */
interface FacilityBookingRepository {
    fun save(booking: FacilityBooking): FacilityBooking

    fun findById(id: BookingId): FacilityBooking?

    /** Bookings of a facility intersecting the [from, to) window. */
    fun findAllForFacilityBetween(facilityId: FacilityId, from: Instant, to: Instant): List<FacilityBooking>

    /** How many existing bookings overlap the given time range. */
    fun countOverlapping(facilityId: FacilityId, startsAt: Instant, endsAt: Instant): Long

    /** How many bookings a resident holds for a facility inside a window. */
    fun countByResidentBetween(
        facilityId: FacilityId,
        residentId: UserId,
        from: Instant,
        to: Instant,
    ): Long

    /** A resident's bookings that have not started yet, across all facilities. */
    fun findUpcomingByResident(residentId: UserId, from: Instant): List<FacilityBooking>

    fun deleteById(id: BookingId)
}
