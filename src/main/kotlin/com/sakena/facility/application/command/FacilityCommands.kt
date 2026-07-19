package com.sakena.facility.application.command

data class CreateFacilityCommand(
    val name: String,
    val icon: String?,
)

data class UpdateFacilityCommand(
    val name: String,
    val icon: String?,
)
