package com.sakena.property.infrastructure.web

import com.sakena.property.application.BuildingService
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.infrastructure.web.dto.BuildingResponse
import com.sakena.property.infrastructure.web.dto.CreateBuildingRequest
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.Principal

@RestController
@RequestMapping("/api/v1/buildings")
@Tag(name = "Buildings", description = "Create, read, update and delete buildings")
class BuildingController(
    private val buildingService: BuildingService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List all buildings")
    @GetMapping
    fun list(principal: Principal): List<BuildingResponse> =
        buildingService.getAll(currentManagerId(principal)).map(BuildingResponse::from)

    @Operation(summary = "Get a building by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String, principal: Principal): BuildingResponse =
        BuildingResponse.from(buildingService.getById(BuildingId.from(id), currentManagerId(principal)))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new building")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateBuildingRequest,
        uriBuilder: UriComponentsBuilder,
        principal: Principal,
    ): ResponseEntity<BuildingResponse> {
        val building = buildingService.create(request.toCommand(), currentManagerId(principal))
        val location: URI = uriBuilder.path("/api/v1/buildings/{id}").build(building.id.value)
        return ResponseEntity.created(location).body(BuildingResponse.from(building))
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a building")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateBuildingRequest,
        principal: Principal,
    ): BuildingResponse =
        BuildingResponse.from(
            buildingService.update(BuildingId.from(id), request.toCommand(), currentManagerId(principal)),
        )

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a building")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String, principal: Principal) =
        buildingService.delete(BuildingId.from(id), currentManagerId(principal))

    private fun currentManagerId(principal: Principal): UserId =
        profileService.getUserByUsername(principal.name)?.id
            ?: throw EntityNotFoundException("Signed-in manager was not found")
}
