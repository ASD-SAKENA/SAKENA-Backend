package com.sakena.facility.domain

import com.sakena.facility.domain.model.BookingId
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import java.time.Instant

interface FacilityBookingRepository {
    fun save(booking: FacilityBooking): FacilityBooking

    fun findById(id: BookingId): FacilityBooking?

    /** Bookings of a facility intersecting the [from, to) window. */
    fun findAllForFacilityBetween(facilityId: FacilityId, from: Instant, to: Instant): List<FacilityBooking>

    /** How many existing bookings overlap the given time range. */
    /** People already booked into the slot, not the number of bookings. */
    fun sumPartySizeOverlapping(facilityId: FacilityId, startsAt: Instant, endsAt: Instant): Long

    /** How many bookings a resident holds for a facility inside a window. */
    fun countByResidentBetween(
        facilityId: FacilityId,
        residentId: UserId,
        from: Instant,
        to: Instant,
    ): Long

    /** A resident's upcoming bookings, limited to their current building. */
    fun findUpcomingByResidentInBuilding(
        residentId: UserId,
        buildingId: BuildingId,
        from: Instant,
    ): List<FacilityBooking>

    fun deleteById(id: BookingId)
}
