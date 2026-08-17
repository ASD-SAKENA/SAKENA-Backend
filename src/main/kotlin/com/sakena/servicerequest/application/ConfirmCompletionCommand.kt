package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId

data class ConfirmCompletionCommand(
    val serviceRequestId: ServiceRequestId,
    val userId: UserId,
    val score: Int,
)
