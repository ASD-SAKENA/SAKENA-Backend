package com.sakena.facility.infrastructure.web.dto

import com.sakena.facility.application.command.BookFacilityCommand
import com.sakena.facility.domain.model.FacilityBooking
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateBookingRequest(
    @field:NotNull(message = "startsAt must not be null")
    val startsAt: Instant,

    @field:NotNull(message = "endsAt must not be null")
    val endsAt: Instant,
) {
    fun toCommand() = BookFacilityCommand(startsAt = startsAt, endsAt = endsAt)
}

data class BookingResponse(
    val id: UUID,
    val facilityId: UUID,
    val bookedBy: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
) {
    companion object {
        fun from(booking: FacilityBooking) = BookingResponse(
            id = booking.id.value,
            facilityId = booking.facilityId.value,
            bookedBy = booking.bookedBy.value,
            startsAt = booking.startsAt,
            endsAt = booking.endsAt,
        )
    }
}
