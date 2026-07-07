package com.sakena.servicerequest.infrastructure.web

import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class CompleteRequest(
    @field:Size(max = 4000, message = "Completion report must be at most 4000 characters")
    val completionReport: String? = null,

    @field:PositiveOrZero(message = "Completion cost must be zero or positive")
    val completionCost: Double? = null
)
