package com.sakena.property.infrastructure.web.dto

import com.sakena.property.application.command.CreateApartmentCommand
import com.sakena.property.application.command.UpdateApartmentCommand
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateApartmentRequest(
    @field:NotNull(message = "buildingId must not be null")
    val buildingId: UUID,

    @field:NotBlank(message = "unitNumber must not be blank")
    @field:Size(max = 50, message = "unitNumber must be at most 50 characters")
    val unitNumber: String,

    @field:Min(value = 0, message = "floorNumber must not be negative")
    val floorNumber: Int,

    @field:DecimalMin(value = "0.01", message = "areaSquareMeters must be greater than zero")
    val areaSquareMeters: BigDecimal,

    @field:Min(value = 0, message = "bedrooms must not be negative")
    @field:Max(value = 20, message = "bedrooms must be at most 20")
    val bedrooms: Int,
) {
    fun toCommand() = CreateApartmentCommand(
        buildingId = BuildingId(buildingId),
        unitNumber = unitNumber,
        floorNumber = floorNumber,
        areaSquareMeters = areaSquareMeters,
        bedrooms = bedrooms,
    )
}

data class UpdateApartmentRequest(
    @field:NotNull(message = "buildingId must not be null")
    val buildingId: UUID,

    @field:NotBlank(message = "unitNumber must not be blank")
    @field:Size(max = 50, message = "unitNumber must be at most 50 characters")
    val unitNumber: String,

    @field:Min(value = 0, message = "floorNumber must not be negative")
    val floorNumber: Int,

    @field:DecimalMin(value = "0.01", message = "areaSquareMeters must be greater than zero")
    val areaSquareMeters: BigDecimal,

    @field:Min(value = 0, message = "bedrooms must not be negative")
    @field:Max(value = 20, message = "bedrooms must be at most 20")
    val bedrooms: Int,
) {
    fun toCommand() = UpdateApartmentCommand(
        buildingId = BuildingId(buildingId),
        unitNumber = unitNumber,
        floorNumber = floorNumber,
        areaSquareMeters = areaSquareMeters,
        bedrooms = bedrooms,
    )
}

data class ApartmentResponse(
    val id: UUID,
    val buildingId: UUID,
    val unitNumber: String,
    val floorNumber: Int,
    val areaSquareMeters: BigDecimal,
    val bedrooms: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(apartment: Apartment) = ApartmentResponse(
            id = apartment.id.value,
            buildingId = apartment.buildingId.value,
            unitNumber = apartment.unitNumber,
            floorNumber = apartment.floorNumber,
            areaSquareMeters = apartment.areaSquareMeters,
            bedrooms = apartment.bedrooms,
            createdAt = apartment.createdAt,
            updatedAt = apartment.updatedAt,
        )
    }
}
