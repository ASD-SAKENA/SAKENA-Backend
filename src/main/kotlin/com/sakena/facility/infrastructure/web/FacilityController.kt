package com.sakena.facility.infrastructure.web

import com.sakena.facility.application.FacilityService
import com.sakena.facility.domain.model.FacilityId
import com.sakena.facility.infrastructure.web.dto.CreateFacilityRequest
import com.sakena.facility.infrastructure.web.dto.FacilityResponse
import com.sakena.facility.infrastructure.web.dto.UpdateFacilityRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
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

/**
 * REST adapter for the Facility bounded context. Controllers stay thin:
 * parse/validate input, delegate to the application service, map the result
 * back to a DTO.
 */
@RestController
@RequestMapping("/api/v1/facilities")
@Tag(name = "Facilities", description = "Create, read, update and delete reservable facilities")
@SecurityRequirement(name = "bearerAuth")
class FacilityController(
    private val facilityService: FacilityService,
) {

    @Operation(summary = "List all facilities")
    @GetMapping
    fun list(): List<FacilityResponse> =
        facilityService.getAll().map(FacilityResponse::from)

    @Operation(summary = "Get a facility by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): FacilityResponse =
        FacilityResponse.from(facilityService.getById(FacilityId.from(id)))

    @Operation(summary = "Create a new facility (manager)")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateFacilityRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<FacilityResponse> {
        val facility = facilityService.create(request.toCommand())
        val location: URI = uriBuilder.path("/api/v1/facilities/{id}").build(facility.id.value)
        return ResponseEntity.created(location).body(FacilityResponse.from(facility))
    }

    @Operation(summary = "Update a facility (manager)")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateFacilityRequest,
    ): FacilityResponse =
        FacilityResponse.from(facilityService.update(FacilityId.from(id), request.toCommand()))

    @Operation(summary = "Delete a facility (manager)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) =
        facilityService.delete(FacilityId.from(id))
}
