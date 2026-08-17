package com.sakena.property.infrastructure.web

import com.sakena.property.application.ApartmentService
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.infrastructure.web.dto.ApartmentResponse
import com.sakena.property.infrastructure.web.dto.CreateApartmentRequest
import com.sakena.property.infrastructure.web.dto.UpdateApartmentRequest
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@RestController
@RequestMapping("/api/v1/apartments")
@Tag(name = "Apartments", description = "Create, read, update and delete apartments")
class ApartmentController(
    private val apartmentService: ApartmentService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List apartments — every building, or just the one the requester manages")
    @GetMapping
    fun list(@RequestParam(required = false) buildingId: String?): List<ApartmentResponse> =
        apartmentService.getAll(buildingId?.let(BuildingId::from), currentUser().managedBuildingId)
            .map(ApartmentResponse::from)

    @Operation(summary = "Get an apartment by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ApartmentResponse =
        ApartmentResponse.from(apartmentService.getById(ApartmentId.from(id)))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a new apartment in the building the requesting manager administers")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateApartmentRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<ApartmentResponse> {
        val apartment = apartmentService.create(request.toCommand(), currentUser().managedBuildingId)
        val location: URI = uriBuilder.path("/api/v1/apartments/{id}").build(apartment.id.value)
        return ResponseEntity.created(location).body(ApartmentResponse.from(apartment))
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update an apartment in the building the requesting manager administers")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateApartmentRequest,
    ): ApartmentResponse =
        ApartmentResponse.from(
            apartmentService.update(ApartmentId.from(id), request.toCommand(), currentUser().managedBuildingId),
        )

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete an apartment from the building the requesting manager administers")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) =
        apartmentService.delete(ApartmentId.from(id), currentUser().managedBuildingId)

    private fun currentUser(): User = SecurityContextHolder.getContext().authentication.name
        .let { username -> profileService.getUserByUsername(username) }
        ?: throw RuntimeException("User not found")
}
