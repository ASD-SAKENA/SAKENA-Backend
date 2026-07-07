package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId
import java.time.Instant

data class StartProgressCommand(
    val serviceRequestId: ServiceRequestId,
    val userId: UserId,
    val expectedCompletionAt: Instant? = null
)
