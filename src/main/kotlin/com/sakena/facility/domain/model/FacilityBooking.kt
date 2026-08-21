package com.sakena.facility.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@JvmInline
value class BookingId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): BookingId = BookingId(UUID.randomUUID())

        fun from(raw: String): BookingId =
            try {
                BookingId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid booking id")
            }
    }
}

class FacilityBooking private constructor(
    val id: BookingId,
    val facilityId: FacilityId,
    val bookedBy: UserId,
    val startsAt: Instant,
    val endsAt: Instant,
    /** How many people this booking brings — capacity is counted in people. */
    val partySize: Int,
    /** What the resident actually paid, frozen so a later price change or a
     *  refund can never disagree with what was taken. */
    val price: BigDecimal,
    val createdAt: Instant,
    cancelledAt: Instant?,
) {
    var cancelledAt: Instant? = cancelledAt
        private set

    val cancelled: Boolean get() = cancelledAt != null

    /**
     * A session that has already begun cannot be called off — the resident
     * had the facility for that time whether or not they turned up, so there
     * is nothing to refund.
     */
    fun cancel(now: Instant = Instant.now()) {
        if (cancelled) {
            throw DomainConflictException("This booking has already been cancelled")
        }
        if (!now.isBefore(startsAt)) {
            throw DomainConflictException("A booking cannot be cancelled once its session has started")
        }
        cancelledAt = now
    }

    companion object {
        const val MIN_PARTY_SIZE = 1

        fun create(
            facilityId: FacilityId,
            bookedBy: UserId,
            startsAt: Instant,
            endsAt: Instant,
            partySize: Int = MIN_PARTY_SIZE,
            price: BigDecimal = BigDecimal.ZERO,
        ): FacilityBooking {
            if (!endsAt.isAfter(startsAt)) {
                throw DomainValidationException("Booking end must be after its start")
            }
            if (partySize < MIN_PARTY_SIZE) {
                throw DomainValidationException("A booking must be for at least one person")
            }
            if (price < BigDecimal.ZERO) {
                throw DomainValidationException("A booking price cannot be negative")
            }
            return FacilityBooking(
                id = BookingId.new(),
                facilityId = facilityId,
                bookedBy = bookedBy,
                startsAt = startsAt,
                endsAt = endsAt,
                partySize = partySize,
                price = price,
                createdAt = Instant.now(),
                cancelledAt = null,
            )
        }

        fun reconstitute(
            id: BookingId,
            facilityId: FacilityId,
            bookedBy: UserId,
            startsAt: Instant,
            endsAt: Instant,
            partySize: Int,
            price: BigDecimal,
            createdAt: Instant,
            cancelledAt: Instant?,
        ): FacilityBooking = FacilityBooking(
            id, facilityId, bookedBy, startsAt, endsAt, partySize, price, createdAt, cancelledAt,
        )
    }
}
