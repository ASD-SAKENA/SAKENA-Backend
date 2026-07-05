package com.sakena.servicerequest.infrastructure.web

import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestStatus
import java.time.Instant

data class ServiceRequestResponse(
    val id: String,
    val title: String,
    val description: String,
    val location: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: ServiceRequestStatus,
    val assignedTo: String?, // user ID as string
    val resolvedAt: Instant?
) {
    companion object {
        fun fromDomain(request: ServiceRequest): ServiceRequestResponse {
            return ServiceRequestResponse(
                id = request.id.value.toString(),
                title = request.title,
                description = request.description,
                location = request.location,
                createdAt = request.createdAt,
                updatedAt = request.updatedAt,
                status = request.status,
                assignedTo = request.assignedTo?.value?.toString(),
                resolvedAt = request.resolvedAt
            )
        }
    }
}
