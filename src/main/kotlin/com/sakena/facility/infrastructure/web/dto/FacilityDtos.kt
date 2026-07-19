package com.sakena.facility.infrastructure.web.dto

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.model.Facility
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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

    @field:Min(value = 1, message = "capacity must be at least 1")
    @field:Max(value = 1000, message = "capacity must be at most 1000")
    val capacity: Int = Facility.DEFAULT_CAPACITY,
) {
    fun toCommand() = CreateFacilityCommand(name = name, icon = icon, capacity = capacity)
}

data class UpdateFacilityRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:Size(max = 50, message = "icon must be at most 50 characters")
    val icon: String? = null,

    @field:Min(value = 1, message = "capacity must be at least 1")
    @field:Max(value = 1000, message = "capacity must be at most 1000")
    val capacity: Int,
) {
    fun toCommand() = UpdateFacilityCommand(name = name, icon = icon, capacity = capacity)
}

data class FacilityResponse(
    val id: UUID,
    val name: String,
    val icon: String?,
    val capacity: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(facility: Facility) = FacilityResponse(
            id = facility.id.value,
            name = facility.name,
            icon = facility.icon,
            capacity = facility.capacity,
            createdAt = facility.createdAt,
            updatedAt = facility.updatedAt,
        )
    }
}
