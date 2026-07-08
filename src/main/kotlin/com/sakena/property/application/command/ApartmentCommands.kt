package com.sakena.property.application.command

import com.sakena.property.domain.model.BuildingId
import java.math.BigDecimal

data class CreateApartmentCommand(
    val buildingId: BuildingId,
    val unitNumber: String,
    val floorNumber: Int,
    val areaSquareMeters: BigDecimal,
    val bedrooms: Int,
)

data class UpdateApartmentCommand(
    val buildingId: BuildingId,
    val unitNumber: String,
    val floorNumber: Int,
    val areaSquareMeters: BigDecimal,
    val bedrooms: Int,
)
