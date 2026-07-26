package com.sakena.facility.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The scheduling rules a facility is booked under — opening hours, closed
 * days, allowed booking length, how far ahead residents may book, how many
 * bookings each may hold per week, and the hourly price.
 *
 * All of it is validated here rather than in the service, so a booking can
 * never bypass a rule by going through a different code path.
 */
data class BookingRules(
    val opensAt: LocalTime,
    val closesAt: LocalTime,
    /** Days the facility is closed, e.g. a pool shut on Fridays. */
    val closedDays: Set<DayOfWeek>,
    val minDurationMinutes: Int,
    val maxDurationMinutes: Int,
    /** How many days ahead a resident may book. */
    val maxAdvanceDays: Int,
    /** Bookings one resident may hold in the same week; 0 means unlimited. */
    val maxPerResidentPerWeek: Int,
    /** Cost per hour; zero means the facility is free. */
    val hourlyPrice: BigDecimal,
) {
    init {
        if (!closesAt.isAfter(opensAt)) {
            throw DomainValidationException("Closing time must be after opening time")
        }
        if (minDurationMinutes < MIN_SLOT_MINUTES) {
            throw DomainValidationException(
                "Minimum booking length must be at least $MIN_SLOT_MINUTES minutes",
            )
        }
        if (maxDurationMinutes < minDurationMinutes) {
            throw DomainValidationException(
                "Maximum booking length must not be shorter than the minimum",
            )
        }
        if (maxAdvanceDays < 1) {
            throw DomainValidationException("Residents must be able to book at least one day ahead")
        }
        if (maxPerResidentPerWeek < 0) {
            throw DomainValidationException("The weekly booking limit must not be negative")
        }
        if (hourlyPrice < BigDecimal.ZERO) {
            throw DomainValidationException("The hourly price must not be negative")
        }
        if (closedDays.size >= DayOfWeek.entries.size) {
            throw DomainValidationException("A facility cannot be closed every day of the week")
        }
    }

    val unlimitedPerWeek: Boolean get() = maxPerResidentPerWeek == 0

    /**
     * Rejects a requested slot that breaks any scheduling rule. Times are
     * compared in [zone], since opening hours are local to the building.
     */
    fun validateSlot(startsAt: Instant, endsAt: Instant, zone: ZoneId, now: Instant) {
        if (!endsAt.isAfter(startsAt)) {
            throw DomainValidationException("Booking end must be after its start")
        }
        if (!startsAt.isAfter(now)) {
            throw DomainConflictException("A slot in the past cannot be booked")
        }
        if (startsAt.isAfter(now.plus(maxAdvanceDays.toLong(), ChronoUnit.DAYS))) {
            throw DomainConflictException("Bookings open only $maxAdvanceDays days in advance")
        }

        val minutes = Duration.between(startsAt, endsAt).toMinutes()
        if (minutes < minDurationMinutes) {
            throw DomainConflictException("The shortest booking is $minDurationMinutes minutes")
        }
        if (minutes > maxDurationMinutes) {
            throw DomainConflictException("The longest booking is $maxDurationMinutes minutes")
        }

        val start = startsAt.atZone(zone)
        val end = endsAt.atZone(zone)
        if (start.dayOfWeek in closedDays) {
            throw DomainConflictException("The facility is closed on this day")
        }
        // A booking has to sit inside one opening period, so it cannot run past midnight.
        if (start.toLocalDate() != end.toLocalDate()) {
            throw DomainConflictException("A booking must start and end on the same day")
        }
        if (start.toLocalTime() < opensAt || end.toLocalTime() > closesAt) {
            throw DomainConflictException("The facility is only open between $opensAt and $closesAt")
        }
    }

    /** Price of a slot, rounded to whole currency units. */
    fun priceFor(startsAt: Instant, endsAt: Instant): BigDecimal {
        if (hourlyPrice <= BigDecimal.ZERO) return BigDecimal.ZERO
        val minutes = Duration.between(startsAt, endsAt).toMinutes()
        return hourlyPrice
            .multiply(BigDecimal(minutes))
            .divide(BigDecimal(60), 0, java.math.RoundingMode.HALF_UP)
    }

    companion object {
        const val MIN_SLOT_MINUTES = 15

        /** Sensible starting point: open 08–22 every day, 30 min to 2 h, free. */
        val DEFAULT = BookingRules(
            opensAt = LocalTime.of(8, 0),
            closesAt = LocalTime.of(22, 0),
            closedDays = emptySet(),
            minDurationMinutes = 30,
            maxDurationMinutes = 120,
            maxAdvanceDays = 30,
            maxPerResidentPerWeek = 0,
            hourlyPrice = BigDecimal.ZERO,
        )
    }
}
