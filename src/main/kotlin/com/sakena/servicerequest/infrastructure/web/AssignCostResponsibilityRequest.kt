package com.sakena.servicerequest.infrastructure.web

import com.sakena.servicerequest.domain.ServiceCostResponsibility
import jakarta.validation.constraints.NotNull

data class AssignCostResponsibilityRequest(
    @field:NotNull(message = "Cost responsibility is required")
    val costResponsibility: ServiceCostResponsibility? = null,
)
