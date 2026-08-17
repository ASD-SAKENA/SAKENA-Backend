package com.sakena.servicerequest.infrastructure.web

data class RequestingUnitResponse(
    val unitNumber: String,
    val floorNumber: Int,
    val buildingName: String,
)
