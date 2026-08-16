package com.sakena.residency.infrastructure.web.dto

import com.sakena.residency.application.ResidencyDetails
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.UserId
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class StartResidencyRequest(
    @field:NotNull(message = "residentId must not be null")
    val residentId: UUID,

    @field:NotNull(message = "tenancy must not be null")
    val tenancy: TenancyType,
) {
    fun toCommand() = StartResidencyCommand(
        residentId = UserId(residentId),
        tenancy = tenancy,
    )
}

/**
 * A residency as the client renders it. The resident's display name is
 * resolved by the controller so the UI never has to look users up itself.
 */
data class ResidencyResponse(
    val id: UUID,
    val apartmentId: UUID,
    val residentId: UUID,
    val residentName: String,
    val unitNumber: String?,
    val buildingId: UUID?,
    val buildingName: String?,
    val floorNumber: Int?,
    val areaSquareMeters: BigDecimal?,
    val bedrooms: Int?,
    val tenancy: TenancyType,
    val movedInAt: Instant,
    val movedOutAt: Instant?,
    val active: Boolean,
) {
    companion object {
        fun from(
            details: ResidencyDetails,
            residentName: String,
        ): ResidencyResponse {
            val residency = details.residency
            return ResidencyResponse(
            id = residency.id.value,
            apartmentId = residency.apartmentId.value,
            residentId = residency.residentId.value,
            residentName = residentName,
            unitNumber = details.unitNumber,
            buildingId = details.buildingId?.value,
            buildingName = details.buildingName,
            floorNumber = details.floorNumber,
            areaSquareMeters = details.areaSquareMeters,
            bedrooms = details.bedrooms,
            tenancy = residency.tenancy,
            movedInAt = residency.movedInAt,
            movedOutAt = residency.movedOutAt,
            active = residency.active,
            )
        }
    }
}
