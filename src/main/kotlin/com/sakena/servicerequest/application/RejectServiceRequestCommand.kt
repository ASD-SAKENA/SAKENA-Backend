package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId

data class RejectServiceRequestCommand(
    val serviceRequestId: ServiceRequestId,
    val userId: UserId,
)
