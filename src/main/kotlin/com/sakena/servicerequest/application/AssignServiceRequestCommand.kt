package com.sakena.servicerequest.application

import com.sakena.user.domain.UserId

data class AssignServiceRequestCommand(
    val serviceRequestId: String,
    val workerId: UserId,
    val userId : UserId,
)
