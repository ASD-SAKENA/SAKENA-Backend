package com.sakena.property.infrastructure.web

import com.sakena.property.application.BuildingService
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.infrastructure.web.dto.BuildingResponse
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
import com.sakena.user.application.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A building is created exactly once, automatically, when its manager
 * registers ([com.sakena.user.application.AuthService.register]) — there is
 * intentionally no create/delete endpoint here, only rename/re-address of
 * the building the requesting manager already administers.
 */
@RestController
@RequestMapping("/api/v1/buildings")
@Tag(name = "Buildings", description = "Read and update buildings")
class BuildingController(
    private val buildingService: BuildingService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List buildings — every building, or just the one the requester manages")
    @GetMapping
    fun list(): List<BuildingResponse> =
        buildingService.getAll(currentUser().managedBuildingId).map(BuildingResponse::from)

    @Operation(summary = "Get a building by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): BuildingResponse =
        BuildingResponse.from(buildingService.getById(BuildingId.from(id)))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update the building the requesting manager administers")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateBuildingRequest,
    ): BuildingResponse {
        val manager = currentUser()
        return BuildingResponse.from(
            buildingService.update(BuildingId.from(id), manager.managedBuildingId, request.toCommand()),
        )
    }

    private fun currentUser() = SecurityContextHolder.getContext().authentication.name
        .let { username -> profileService.getUserByUsername(username) }
        ?: throw RuntimeException("User not found")
}
