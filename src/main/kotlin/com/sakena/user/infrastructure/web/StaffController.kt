package com.sakena.user.infrastructure.web

import com.sakena.rating.application.RatingService
import com.sakena.user.application.StaffDirectoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@PreAuthorize("hasRole('MANAGER')")
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff", description = "Active service staff, for assigning service requests (manager only)")
@SecurityRequirement(name = "bearerAuth")
class StaffController(
    private val staffDirectoryService: StaffDirectoryService,
    private val ratingService: RatingService,
) {

    @GetMapping
    @Operation(summary = "List active service staff with their average rating")
    fun list(): List<StaffSummaryResponse> {
        val staff = staffDirectoryService.getActiveStaff()
        val averages = ratingService.getAverageFor(staff.map { it.id })
        return staff.map { StaffSummaryResponse.from(it, averages[it.id]) }
    }
}
