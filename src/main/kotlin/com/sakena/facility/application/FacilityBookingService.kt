package com.sakena.facility.application

import com.sakena.facility.application.command.BookFacilityCommand
import com.sakena.facility.domain.BookingNotFoundException
import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.BookingId
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.facility.domain.model.FacilityId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Application service for booking facilities. Enforces the capacity rule:
 * once a facility's concurrent-booking limit is reached for a time range,
 * that slot is locked and further bookings are rejected.
 */
@Service
@Transactional
class FacilityBookingService(
    private val facilityRepository: FacilityRepository,
    private val bookingRepository: FacilityBookingRepository,
) {

    fun book(facilityId: FacilityId, command: BookFacilityCommand, bookedBy: UserId): FacilityBooking {
        val facility = facilityRepository.findById(facilityId)
            ?: throw FacilityNotFoundException(facilityId)

        val overlapping = bookingRepository.countOverlapping(facilityId, command.startsAt, command.endsAt)
        if (overlapping >= facility.capacity) {
            throw DomainConflictException(
                "Facility '${facility.name}' is fully booked for this time slot (capacity ${facility.capacity})"
            )
        }

        val booking = FacilityBooking.create(facilityId, bookedBy, command.startsAt, command.endsAt)
        return bookingRepository.save(booking)
    }

    fun cancel(facilityId: FacilityId, bookingId: BookingId, requestedBy: UserId) {
        val booking = bookingRepository.findById(bookingId)
            ?: throw BookingNotFoundException(bookingId)
        if (booking.facilityId != facilityId) throw BookingNotFoundException(bookingId)
        if (booking.bookedBy != requestedBy) {
            throw DomainConflictException("Only the resident who made a booking can cancel it")
        }
        bookingRepository.deleteById(bookingId)
    }

    @Transactional(readOnly = true)
    fun getForFacilityBetween(facilityId: FacilityId, from: Instant, to: Instant): List<FacilityBooking> {
        if (!facilityRepository.existsById(facilityId)) throw FacilityNotFoundException(facilityId)
        return bookingRepository.findAllForFacilityBetween(facilityId, from, to)
    }
}
