package com.sakena.property.infrastructure.web.dto

import com.sakena.property.application.command.CreateBuildingCommand
import com.sakena.property.application.command.UpdateBuildingCommand
import com.sakena.property.domain.model.Building
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateBuildingRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:NotBlank(message = "address must not be blank")
    @field:Size(max = 500, message = "address must be at most 500 characters")
    val address: String,
) {
    fun toCommand() = CreateBuildingCommand(name = name, address = address)
}

data class UpdateBuildingRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:NotBlank(message = "address must not be blank")
    @field:Size(max = 500, message = "address must be at most 500 characters")
    val address: String,
) {
    fun toCommand() = UpdateBuildingCommand(name = name, address = address)
}

data class BuildingResponse(
    val id: UUID,
    val name: String,
    val address: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(building: Building) = BuildingResponse(
            id = building.id.value,
            name = building.name,
            address = building.address,
            createdAt = building.createdAt,
            updatedAt = building.updatedAt,
        )
    }
}
