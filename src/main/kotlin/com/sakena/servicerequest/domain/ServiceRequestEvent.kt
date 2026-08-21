package com.sakena.servicerequest.domain

import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

data class ServiceRequestEvent(
    val id: UUID = UUID.randomUUID(),
    val serviceRequestId: ServiceRequestId,
    val type: ServiceRequestEventType,
    val occurredAt: Instant = Instant.now(),
    val performedBy: UserId? = null,
    val payload: String? = null,
)
