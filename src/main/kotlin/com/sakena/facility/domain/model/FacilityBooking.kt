package com.sakena.facility.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
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
    val createdAt: Instant,
) {

    companion object {
        const val MIN_PARTY_SIZE = 1

        fun create(
            facilityId: FacilityId,
            bookedBy: UserId,
            startsAt: Instant,
            endsAt: Instant,
            partySize: Int = MIN_PARTY_SIZE,
        ): FacilityBooking {
            if (!endsAt.isAfter(startsAt)) {
                throw DomainValidationException("Booking end must be after its start")
            }
            if (partySize < MIN_PARTY_SIZE) {
                throw DomainValidationException("A booking must be for at least one person")
            }
            return FacilityBooking(
                id = BookingId.new(),
                facilityId = facilityId,
                bookedBy = bookedBy,
                startsAt = startsAt,
                endsAt = endsAt,
                partySize = partySize,
                createdAt = Instant.now(),
            )
        }

        fun reconstitute(
            id: BookingId,
            facilityId: FacilityId,
            bookedBy: UserId,
            startsAt: Instant,
            endsAt: Instant,
            partySize: Int,
            createdAt: Instant,
        ): FacilityBooking =
            FacilityBooking(id, facilityId, bookedBy, startsAt, endsAt, partySize, createdAt)
    }
}
