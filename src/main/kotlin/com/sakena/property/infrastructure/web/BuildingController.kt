package com.sakena.property.infrastructure.web

import com.sakena.property.application.BuildingService
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.infrastructure.web.dto.BuildingResponse
import com.sakena.property.infrastructure.web.dto.CreateBuildingRequest
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@RestController
@RequestMapping("/api/v1/buildings")
@Tag(name = "Buildings", description = "Create, read, update and delete buildings")
class BuildingController(
    private val buildingService: BuildingService,
) {

    @Operation(summary = "List all buildings")
    @GetMapping
    fun list(): List<BuildingResponse> =
        buildingService.getAll().map(BuildingResponse::from)

    @Operation(summary = "Get a building by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): BuildingResponse =
        BuildingResponse.from(buildingService.getById(BuildingId.from(id)))

    @Operation(summary = "Create a new building")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateBuildingRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<BuildingResponse> {
        val building = buildingService.create(request.toCommand())
        val location: URI = uriBuilder.path("/api/v1/buildings/{id}").build(building.id.value)
        return ResponseEntity.created(location).body(BuildingResponse.from(building))
    }

    @Operation(summary = "Update a building")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateBuildingRequest,
    ): BuildingResponse =
        BuildingResponse.from(buildingService.update(BuildingId.from(id), request.toCommand()))

    @Operation(summary = "Delete a building")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) =
        buildingService.delete(BuildingId.from(id))
}
