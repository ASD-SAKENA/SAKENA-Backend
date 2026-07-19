package com.sakena.facility.application.command

import com.sakena.facility.domain.model.Facility

data class CreateFacilityCommand(
    val name: String,
    val icon: String?,
    val capacity: Int = Facility.DEFAULT_CAPACITY,
)

data class UpdateFacilityCommand(
    val name: String,
    val icon: String?,
    val capacity: Int,
)
