package com.sakena.property.infrastructure.web

import com.sakena.property.application.ApartmentService
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.infrastructure.web.dto.ApartmentResponse
import com.sakena.property.infrastructure.web.dto.CreateApartmentRequest
import com.sakena.property.infrastructure.web.dto.UpdateApartmentRequest
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
) {

    @Operation(summary = "List all apartments")
    @GetMapping
    fun list(@RequestParam(required = false) buildingId: String?): List<ApartmentResponse> =
        apartmentService.getAll(buildingId?.let(BuildingId::from)).map(ApartmentResponse::from)

    @Operation(summary = "Get an apartment by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ApartmentResponse =
        ApartmentResponse.from(apartmentService.getById(ApartmentId.from(id)))

    @Operation(summary = "Create a new apartment")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateApartmentRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<ApartmentResponse> {
        val apartment = apartmentService.create(request.toCommand())
        val location: URI = uriBuilder.path("/api/v1/apartments/{id}").build(apartment.id.value)
        return ResponseEntity.created(location).body(ApartmentResponse.from(apartment))
    }

    @Operation(summary = "Update an apartment")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateApartmentRequest,
    ): ApartmentResponse =
        ApartmentResponse.from(apartmentService.update(ApartmentId.from(id), request.toCommand()))

    @Operation(summary = "Delete an apartment")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) =
        apartmentService.delete(ApartmentId.from(id))
}
