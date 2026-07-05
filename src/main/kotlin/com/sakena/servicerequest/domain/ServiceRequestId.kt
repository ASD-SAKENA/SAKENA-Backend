package com.sakena.servicerequest.domain

import java.util.UUID

@JvmInline
value class ServiceRequestId(val value: UUID) {
    companion object {
        fun generate() = ServiceRequestId(UUID.randomUUID())
        fun fromString(id: String) = ServiceRequestId(UUID.fromString(id))
    }
}
