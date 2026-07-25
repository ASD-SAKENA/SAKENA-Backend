package com.sakena.residency.infrastructure.web

import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.ResidencyService
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.infrastructure.web.dto.ResidencyResponse
import com.sakena.residency.infrastructure.web.dto.StartResidencyRequest
import com.sakena.user.application.ProfileService
import com.sakena.user.application.UserDirectory
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
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

/**
 * REST adapter for unit occupancy: the manager assigns and ends residencies,
 * and any resident can look up the unit they occupy.
 */
@RestController
@RequestMapping("/api/v1/residencies")
@Tag(name = "Residencies", description = "Who occupies which unit, and on what basis")
@SecurityRequirement(name = "bearerAuth")
class ResidencyController(
    private val residencyService: ResidencyService,
    private val userDirectory: UserDirectory,
    private val profileService: ProfileService,
) {

    @Operation(summary = "The unit the signed-in resident occupies, if any")
    @GetMapping("/me")
    fun myResidency(): ResidencyResponse? {
        val details = residencyService.getMyResidency(getCurrentUserId()) ?: return null
        val names = userDirectory.usernamesByIds(setOf(details.residency.residentId))
        return ResidencyResponse.from(
            details,
            names[details.residency.residentId] ?: "کاربر حذف‌شده",
        )
    }

    @Operation(summary = "Active residencies of a building — one row per occupied unit")
    @GetMapping
    fun listByBuilding(@RequestParam buildingId: String): List<ResidencyResponse> =
        toResponses(residencyService.getActiveByBuilding(BuildingId.from(buildingId)))

    @Operation(summary = "Occupancy history of a unit, newest first")
    @GetMapping("/apartments/{apartmentId}")
    fun history(@PathVariable apartmentId: String): List<ResidencyResponse> =
        toResponses(residencyService.getHistory(ApartmentId.from(apartmentId)))

    @Operation(summary = "Assign a resident to a unit (manager)")
    @PostMapping("/apartments/{apartmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    fun start(
        @PathVariable apartmentId: String,
        @Valid @RequestBody request: StartResidencyRequest,
    ): ResidencyResponse =
        toResponse(residencyService.start(ApartmentId.from(apartmentId), request.toCommand()))

    @Operation(summary = "Move the current resident out, leaving the unit vacant (manager)")
    @DeleteMapping("/apartments/{apartmentId}")
    @PreAuthorize("hasRole('MANAGER')")
    fun endCurrent(@PathVariable apartmentId: String): ResidencyResponse =
        toResponse(residencyService.endCurrent(ApartmentId.from(apartmentId)))

    private fun toResponse(residency: Residency): ResidencyResponse =
        toResponses(listOf(residency)).first()

    /** Resolves every resident name in one lookup rather than one per row. */
    private fun toResponses(residencies: List<Residency>): List<ResidencyResponse> {
        if (residencies.isEmpty()) return emptyList()
        val names = userDirectory.usernamesByIds(residencies.map { it.residentId }.toSet())
        return residencies.map {
            ResidencyResponse.from(
                residencyService.describe(it),
                names[it.residentId] ?: "کاربر حذف‌شده",
            )
        }
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }
}
