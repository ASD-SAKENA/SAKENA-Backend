package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId

data class AssignServiceCostResponsibilityCommand(
    val serviceRequestId: ServiceRequestId,
    val responsibility: ServiceCostResponsibility,
    val managerId: UserId,
)
