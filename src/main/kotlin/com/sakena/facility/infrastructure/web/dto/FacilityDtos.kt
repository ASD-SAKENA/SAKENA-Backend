package com.sakena.facility.infrastructure.web.dto

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.model.Facility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateFacilityRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:Size(max = 50, message = "icon must be at most 50 characters")
    val icon: String? = null,
) {
    fun toCommand() = CreateFacilityCommand(name = name, icon = icon)
}

data class UpdateFacilityRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:Size(max = 50, message = "icon must be at most 50 characters")
    val icon: String? = null,
) {
    fun toCommand() = UpdateFacilityCommand(name = name, icon = icon)
}

data class FacilityResponse(
    val id: UUID,
    val name: String,
    val icon: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(facility: Facility) = FacilityResponse(
            id = facility.id.value,
            name = facility.name,
            icon = facility.icon,
            createdAt = facility.createdAt,
            updatedAt = facility.updatedAt,
        )
    }
}
