package com.sakena.facility.application

import com.sakena.facility.application.command.BookFacilityCommand
import com.sakena.facility.domain.BookingNotFoundException
import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.BookingId
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.facility.domain.model.FacilityId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Application service for booking facilities. Scheduling rules live on the
 * facility's [com.sakena.facility.domain.model.BookingRules]; this service
 * enforces the two rules that need to see other bookings — the capacity lock
 * and each resident's weekly quota.
 */
@Service
@Transactional
class FacilityBookingService(
    private val facilityRepository: FacilityRepository,
    private val bookingRepository: FacilityBookingRepository,
    @Value("\${app.timezone:Asia/Tehran}")
    private val timezone: String,
) {

    private val zone: ZoneId get() = ZoneId.of(timezone)

    fun book(
        facilityId: FacilityId,
        command: BookFacilityCommand,
        bookedBy: UserId,
    ): FacilityBooking {
        val facility = facilityRepository.findById(facilityId)
            ?: throw FacilityNotFoundException(facilityId)

        facility.rules.validateSlot(command.startsAt, command.endsAt, zone, Instant.now())
        requireWeeklyQuota(facility, command.startsAt, bookedBy)

        val overlapping =
            bookingRepository.countOverlapping(facilityId, command.startsAt, command.endsAt)
        if (overlapping >= facility.capacity) {
            throw DomainConflictException(
                "Facility '${facility.name}' is fully booked for this time slot (capacity ${facility.capacity})",
            )
        }

        val booking = FacilityBooking.create(
            facilityId = facilityId,
            bookedBy = bookedBy,
            startsAt = command.startsAt,
            endsAt = command.endsAt,
        )
        return bookingRepository.save(booking)
    }

    /** The owner cancels their own booking; a manager may cancel any. */
    fun cancel(facilityId: FacilityId, bookingId: BookingId, requestedBy: User) {
        val booking = bookingRepository.findById(bookingId)
            ?: throw BookingNotFoundException(bookingId)
        if (booking.facilityId != facilityId) throw BookingNotFoundException(bookingId)
        if (booking.bookedBy != requestedBy.id && requestedBy.role != Role.MANAGER) {
            throw DomainConflictException(
                "Only the resident who made a booking, or the building manager, can cancel it",
            )
        }
        bookingRepository.deleteById(bookingId)
    }

    @Transactional(readOnly = true)
    fun getForFacilityBetween(
        facilityId: FacilityId,
        from: Instant,
        to: Instant,
    ): List<FacilityBooking> {
        if (!facilityRepository.existsById(facilityId)) throw FacilityNotFoundException(facilityId)
        return bookingRepository.findAllForFacilityBetween(facilityId, from, to)
    }

    /** A resident's own upcoming bookings across every facility. */
    @Transactional(readOnly = true)
    fun getUpcomingFor(residentId: UserId): List<FacilityBooking> =
        bookingRepository.findUpcomingByResident(residentId, Instant.now())

    /** What the slot would cost, so the client shows a price before booking. */
    @Transactional(readOnly = true)
    fun quote(facilityId: FacilityId, startsAt: Instant, endsAt: Instant): BigDecimal {
        val facility = facilityRepository.findById(facilityId)
            ?: throw FacilityNotFoundException(facilityId)
        return facility.rules.priceFor(startsAt, endsAt)
    }

    private fun requireWeeklyQuota(facility: Facility, startsAt: Instant, bookedBy: UserId) {
        val rules = facility.rules
        if (rules.unlimitedPerWeek) return

        // The quota is per calendar week of the slot being booked, in local time.
        val local = startsAt.atZone(zone)
        val weekStart = local.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
        val weekEnd = weekStart.plus(java.time.Duration.ofDays(7))

        val held = bookingRepository.countByResidentBetween(
            facility.id,
            bookedBy,
            weekStart,
            weekEnd,
        )
        if (held >= rules.maxPerResidentPerWeek) {
            throw DomainConflictException(
                "You already hold ${rules.maxPerResidentPerWeek} bookings for " +
                    "'${facility.name}' this week",
            )
        }
    }
}
