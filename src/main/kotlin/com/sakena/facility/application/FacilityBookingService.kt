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
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
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

@Service
@Transactional
class FacilityBookingService(
    private val facilityRepository: FacilityRepository,
    private val bookingRepository: FacilityBookingRepository,
    private val buildingAccess: BuildingAccess,
    @Value("\${app.timezone:Asia/Tehran}")
    private val timezone: String,
) {

    private val zone: ZoneId get() = ZoneId.of(timezone)

    fun book(
        facilityId: FacilityId,
        command: BookFacilityCommand,
        requestedBy: User,
    ): FacilityBooking {
        if (requestedBy.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can book facilities")
        }
        val buildingId = buildingAccess.residentBuildingId(requestedBy.id)
        val facility = requireFacility(facilityId, buildingId)

        facility.rules.validateSlot(command.startsAt, command.endsAt, zone, Instant.now())
        requireWeeklyQuota(facility, command.startsAt, requestedBy.id)

        val overlapping =
            bookingRepository.countOverlapping(facilityId, command.startsAt, command.endsAt)
        if (overlapping >= facility.capacity) {
            throw DomainConflictException(
                "Facility '${facility.name}' is fully booked for this time slot (capacity ${facility.capacity})",
            )
        }

        val booking = FacilityBooking.create(
            facilityId = facilityId,
            bookedBy = requestedBy.id,
            startsAt = command.startsAt,
            endsAt = command.endsAt,
        )
        return bookingRepository.save(booking)
    }

    /** The owner cancels their own booking; a manager may cancel any. */
    fun cancel(facilityId: FacilityId, bookingId: BookingId, requestedBy: User) {
        requireFacility(facilityId, accessibleBuildingId(requestedBy))
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
        requestedBy: User,
    ): List<FacilityBooking> {
        requireFacility(facilityId, accessibleBuildingId(requestedBy))
        return bookingRepository.findAllForFacilityBetween(facilityId, from, to)
    }

    /** A resident's own upcoming bookings in their current building. */
    @Transactional(readOnly = true)
    fun getUpcomingFor(requestedBy: User): List<FacilityBooking> {
        if (requestedBy.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can view their bookings")
        }
        val buildingId = buildingAccess.residentBuildingId(requestedBy.id)
        return bookingRepository.findUpcomingByResidentInBuilding(requestedBy.id, buildingId, Instant.now())
    }

    /** What the slot would cost, so the client shows a price before booking. */
    @Transactional(readOnly = true)
    fun quote(
        facilityId: FacilityId,
        startsAt: Instant,
        endsAt: Instant,
        requestedBy: User,
    ): BigDecimal {
        val facility = requireFacility(facilityId, accessibleBuildingId(requestedBy))
        return facility.rules.priceFor(startsAt, endsAt)
    }

    private fun requireFacility(facilityId: FacilityId, buildingId: BuildingId): Facility =
        facilityRepository.findByIdAndBuildingId(facilityId, buildingId)
            ?: throw FacilityNotFoundException(facilityId)

    private fun accessibleBuildingId(requestedBy: User): BuildingId =
        when (requestedBy.role) {
            Role.MANAGER -> buildingAccess.managedBuildingId(requestedBy.id)
            Role.RESIDENT -> buildingAccess.residentBuildingId(requestedBy.id)
            Role.STAFF -> throw DomainForbiddenException("Staff cannot access facility bookings")
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
