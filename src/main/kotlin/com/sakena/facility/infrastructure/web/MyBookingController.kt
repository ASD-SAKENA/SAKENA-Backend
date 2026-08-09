package com.sakena.facility.infrastructure.web

import com.sakena.facility.application.FacilityBookingService
import com.sakena.facility.application.FacilityService
import com.sakena.facility.infrastructure.web.dto.MyBookingResponse
import com.sakena.user.application.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The signed-in resident's own upcoming reservations, across every facility.
 * Lives on its own literal path so it never collides with the
 * `/api/v1/facilities/{id}` template.
 */
@RestController
@RequestMapping("/api/v1/facilities/my-bookings")
@Tag(name = "Facility Bookings", description = "Reserve facility time slots with capacity enforcement")
@SecurityRequirement(name = "bearerAuth")
class MyBookingController(
    private val bookingService: FacilityBookingService,
    private val facilityService: FacilityService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List your own upcoming bookings")
    @GetMapping
    fun list(): List<MyBookingResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw IllegalStateException("Authenticated user '$username' no longer exists")

        val bookings = bookingService.getUpcomingFor(user.id)
        if (bookings.isEmpty()) return emptyList()

        // One read of the (small) facility list beats an N+1 lookup per booking.
        val facilities = facilityService.getAll().associateBy { it.id }
        return bookings.mapNotNull { booking ->
            facilities[booking.facilityId]?.let { MyBookingResponse.from(booking, it) }
        }
    }
}
