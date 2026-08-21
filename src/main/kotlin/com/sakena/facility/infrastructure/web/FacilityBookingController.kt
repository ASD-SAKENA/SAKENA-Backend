package com.sakena.facility.infrastructure.web

import com.sakena.facility.application.FacilityBookingService
import com.sakena.facility.domain.model.BookingId
import com.sakena.facility.domain.model.FacilityId
import com.sakena.facility.infrastructure.web.dto.BookingQuoteResponse
import com.sakena.facility.infrastructure.web.dto.BookingResponse
import com.sakena.facility.infrastructure.web.dto.CreateBookingRequest
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * REST adapter for facility bookings, nested under a facility. Booking a
 * full slot is rejected with 409 once the facility capacity is reached.
 */
@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/bookings")
@Tag(name = "Facility Bookings", description = "Reserve facility time slots with capacity enforcement")
@SecurityRequirement(name = "bearerAuth")
class FacilityBookingController(
    private val bookingService: FacilityBookingService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List a facility's bookings intersecting a time window")
    @GetMapping
    fun list(
        @PathVariable facilityId: String,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
    ): List<BookingResponse> =
        bookingService.getForFacilityBetween(FacilityId.from(facilityId), from, to, currentUser())
            .map(BookingResponse::from)

    @Operation(summary = "Book a facility time slot (locked once capacity is full)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun book(
        @PathVariable facilityId: String,
        @Valid @RequestBody request: CreateBookingRequest,
    ): BookingResponse {
        val booking = bookingService.book(
            FacilityId.from(facilityId),
            request.toCommand(),
            currentUser(),
        )
        return BookingResponse.from(booking)
    }

    @Operation(summary = "What a slot would cost, before booking it")
    @GetMapping("/quote")
    fun quote(
        @PathVariable facilityId: String,
        @RequestParam startsAt: Instant,
        @RequestParam endsAt: Instant,
        @RequestParam(required = false, defaultValue = "1") partySize: Int,
    ): BookingQuoteResponse =
        BookingQuoteResponse(
            bookingService.quote(
                FacilityId.from(facilityId),
                startsAt,
                endsAt,
                currentUser(),
                partySize,
            ),
        )

    @Operation(summary = "Cancel a booking (your own, or any of them as manager)")
    @DeleteMapping("/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @PathVariable facilityId: String,
        @PathVariable bookingId: String,
    ) {
        bookingService.cancel(
            FacilityId.from(facilityId),
            BookingId.from(bookingId),
            currentUser(),
        )
    }

    private fun currentUser(): User {
        val username = SecurityContextHolder.getContext().authentication.name
        return profileService.getUserByUsername(username)
            ?: throw IllegalStateException("Authenticated user '$username' no longer exists")
    }
}
