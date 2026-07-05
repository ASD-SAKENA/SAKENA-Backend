package com.sakena.servicerequest.domain

import com.sakena.user.domain.UserId
import java.time.Instant

data class ServiceRequest(
    val id: ServiceRequestId,
    val title: String,
    val description: String,
    val location: String?,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: ServiceRequestStatus,
    val assignedTo: UserId? = null,
    val resolvedAt: Instant? = null
) {
    companion object {
        fun create(
            title: String,
            description: String,
            location: String?,
            createdBy: UserId
        ): ServiceRequest {
            require(title.isNotBlank()) { "Title cannot be blank" }
            require(description.isNotBlank()) { "Description cannot be blank" }
            val now = Instant.now()
            return ServiceRequest(
                id = ServiceRequestId.generate(),
                title = title.trim(),
                description = description.trim(),
                location = location?.trim(),
                createdBy = createdBy,
                createdAt = now,
                updatedAt = now,
                status = ServiceRequestStatus.PENDING
            )
        }

        fun reconstitute(
            id: ServiceRequestId,
            title: String,
            description: String,
            location: String?,
            createdBy: UserId,
            createdAt: Instant,
            updatedAt: Instant,
            status: ServiceRequestStatus,
            assignedTo: UserId?,
            resolvedAt: Instant?
        ) = ServiceRequest(
            id, title, description, location, createdBy, createdAt, updatedAt, status, assignedTo, resolvedAt
        )
    }

    fun assignTo(workerId: UserId): ServiceRequest {
        return this.copy(
            assignedTo = workerId,
            status = ServiceRequestStatus.APPROVED,
            updatedAt = Instant.now()
        )
    }

    fun startProgress(): ServiceRequest {
        require(status == ServiceRequestStatus.APPROVED) { "Can only start progress when approved" }
        return this.copy(
            status = ServiceRequestStatus.IN_PROGRESS,
            updatedAt = Instant.now()
        )
    }

    fun complete(): ServiceRequest {
        require(status == ServiceRequestStatus.IN_PROGRESS) { "Can only complete when in progress" }
        return this.copy(
            status = ServiceRequestStatus.COMPLETED,
            resolvedAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    fun reject(): ServiceRequest {
        require(status == ServiceRequestStatus.PENDING) { "Can only reject pending requests" }
        return this.copy(
            status = ServiceRequestStatus.REJECTED,
            updatedAt = Instant.now()
        )
    }
}
