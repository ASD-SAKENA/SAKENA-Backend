package com.sakena.property.application.command

data class CreateBuildingCommand(
    val name: String,
    val address: String,
)

data class UpdateBuildingCommand(
    val name: String,
    val address: String,
)
