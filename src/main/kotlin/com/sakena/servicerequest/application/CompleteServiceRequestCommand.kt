package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId

data class CompleteServiceRequestCommand(
    val serviceRequestId: ServiceRequestId,
    val userId: UserId,
    val completionReport: String? = null,
    val completionCost: Double? = null
)
