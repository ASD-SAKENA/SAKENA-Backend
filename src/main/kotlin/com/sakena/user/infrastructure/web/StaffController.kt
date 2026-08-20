package com.sakena.user.infrastructure.web

import com.sakena.rating.application.RatingService
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import com.sakena.user.application.StaffDirectoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@PreAuthorize("hasRole('MANAGER')")
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff", description = "Active service staff, for assigning service requests (manager only)")
@SecurityRequirement(name = "bearerAuth")
class StaffController(
    private val staffDirectoryService: StaffDirectoryService,
    private val ratingService: RatingService,
    private val profileService: ProfileService,
) {

    @GetMapping
    @Operation(summary = "Active staff serving the requesting manager's building, with their average rating")
    fun list(principal: Principal): List<StaffSummaryResponse> {
        val manager = profileService.getUserByUsername(principal.name)
            ?: throw EntityNotFoundException("Signed-in manager was not found")
        val staff = staffDirectoryService.getActiveStaff(manager.managedBuildingId)
        val averages = ratingService.getAverageFor(staff.map { it.id })
        return staff.map { StaffSummaryResponse.from(it, averages[it.id]) }
    }
}
