package com.sakena.facility.application.command

import com.sakena.facility.domain.model.BookingRules
import com.sakena.facility.domain.model.Facility

data class CreateFacilityCommand(
    val name: String,
    val icon: String?,
    val capacity: Int = Facility.DEFAULT_CAPACITY,
    val rules: BookingRules = BookingRules.DEFAULT,
)

data class UpdateFacilityCommand(
    val name: String,
    val icon: String?,
    val capacity: Int,
    val rules: BookingRules = BookingRules.DEFAULT,
)
