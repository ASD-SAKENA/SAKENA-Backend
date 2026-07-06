package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId

data class ApproveServiceRequestCommand(
    val serviceRequestId: ServiceRequestId,
    val userId : UserId,
)
