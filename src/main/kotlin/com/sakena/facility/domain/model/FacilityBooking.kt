package com.sakena.facility.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

/**
 * Value object identifying a [FacilityBooking].
 */
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

/**
 * FacilityBooking aggregate — a resident's reservation of a facility for a
 * time range. Bookings are immutable; cancelling one deletes it.
 */
class FacilityBooking private constructor(
    val id: BookingId,
    val facilityId: FacilityId,
    val bookedBy: UserId,
    val startsAt: Instant,
    val endsAt: Instant,
    val createdAt: Instant,
) {

    companion object {
        fun create(
            facilityId: FacilityId,
            bookedBy: UserId,
            startsAt: Instant,
            endsAt: Instant,
        ): FacilityBooking {
            if (!endsAt.isAfter(startsAt)) {
                throw DomainValidationException("Booking end must be after its start")
            }
            return FacilityBooking(
                id = BookingId.new(),
                facilityId = facilityId,
                bookedBy = bookedBy,
                startsAt = startsAt,
                endsAt = endsAt,
                createdAt = Instant.now(),
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: BookingId,
            facilityId: FacilityId,
            bookedBy: UserId,
            startsAt: Instant,
            endsAt: Instant,
            createdAt: Instant,
        ): FacilityBooking = FacilityBooking(id, facilityId, bookedBy, startsAt, endsAt, createdAt)
    }
}
