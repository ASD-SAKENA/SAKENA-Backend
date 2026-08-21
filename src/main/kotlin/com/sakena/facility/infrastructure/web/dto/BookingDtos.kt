package com.sakena.facility.infrastructure.web.dto

import com.sakena.facility.application.command.BookFacilityCommand
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityBooking
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateBookingRequest(
    @field:NotNull(message = "startsAt must not be null")
    val startsAt: Instant,

    @field:NotNull(message = "endsAt must not be null")
    val endsAt: Instant,

    /** Defaults to one person so an older client keeps working unchanged. */
    @field:Min(1, message = "partySize must be at least 1")
    val partySize: Int = 1,
) {
    fun toCommand() = BookFacilityCommand(
        startsAt = startsAt,
        endsAt = endsAt,
        partySize = partySize,
    )
}

data class BookingResponse(
    val id: UUID,
    val facilityId: UUID,
    val bookedBy: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val partySize: Int,
    val price: BigDecimal,
) {
    companion object {
        fun from(booking: FacilityBooking) = BookingResponse(
            id = booking.id.value,
            facilityId = booking.facilityId.value,
            bookedBy = booking.bookedBy.value,
            startsAt = booking.startsAt,
            endsAt = booking.endsAt,
            partySize = booking.partySize,
            price = booking.price,
        )
    }
}

/** What a slot would cost, so the client can show a price before booking. */
data class BookingQuoteResponse(val price: BigDecimal)

/** A resident's own booking, carrying the facility name so no second call is needed. */
data class MyBookingResponse(
    val id: UUID,
    val facilityId: UUID,
    val facilityName: String,
    val facilityIcon: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val partySize: Int,
    val price: BigDecimal,
) {
    companion object {
        fun from(booking: FacilityBooking, facility: Facility) = MyBookingResponse(
            id = booking.id.value,
            facilityId = facility.id.value,
            facilityName = facility.name,
            facilityIcon = facility.icon,
            startsAt = booking.startsAt,
            endsAt = booking.endsAt,
            partySize = booking.partySize,
            price = booking.price,
        )
    }
}
