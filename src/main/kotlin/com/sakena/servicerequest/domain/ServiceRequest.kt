package com.sakena.servicerequest.domain

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant

data class ServiceRequest(
    val id: ServiceRequestId,
    val title: String,
    val description: String,
    val location: String?,
    val categoryGroup: ServiceCategoryGroup,
    val subCategory: ServiceSubCategory,
    val createdBy: UserId,
    val updatedBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: ServiceRequestStatus,
    val assignedTo: UserId? = null,
    val resolvedAt: Instant? = null,
    val expectedCompletionAt: Instant? = null,
    val completionReport: String? = null,
    val completionCost: Double? = null
) {
    companion object {
        fun create(
            title: String,
            description: String,
            location: String?,
            createdBy: UserId,
            categoryGroup: ServiceCategoryGroup,
            subCategory: ServiceSubCategory
        ): ServiceRequest {
            validate(
                title = title,
                description = description,
                location = location,
                categoryGroup = categoryGroup,
                subCategory = subCategory,
            )
            val now = Instant.now()
            return ServiceRequest(
                id = ServiceRequestId.generate(),
                title = title.trim(),
                description = description.trim(),
                location = location?.trim(),
                categoryGroup = categoryGroup,
                subCategory = subCategory,
                createdBy = createdBy,
                updatedBy = createdBy,
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
            categoryGroup: ServiceCategoryGroup,
            subCategory: ServiceSubCategory,
            createdBy: UserId,
            updatedBy: UserId,
            createdAt: Instant,
            updatedAt: Instant,
            status: ServiceRequestStatus,
            assignedTo: UserId?,
            resolvedAt: Instant?,
            expectedCompletionAt: Instant? = null,
            completionReport: String? = null,
            completionCost: Double? = null
        ) = ServiceRequest(
            id, title, description, location, categoryGroup, subCategory,
            createdBy, updatedBy, createdAt, updatedAt, status,
            assignedTo, resolvedAt, expectedCompletionAt, completionReport, completionCost
        ).also {
            validate(
                title = it.title,
                description = it.description,
                location = it.location,
                categoryGroup = it.categoryGroup,
                subCategory = it.subCategory,
            )
        }

        fun validate(
            title: String,
            description: String,
            location: String?,
            categoryGroup: ServiceCategoryGroup,
            subCategory: ServiceSubCategory,
        ) {
            if (title.isBlank()) {
                throw DomainValidationException("Title is required")
            }
            if (description.isBlank()) {
                throw DomainValidationException("Description is required")
            }
            if (location != null && location.isBlank()) {
                throw DomainValidationException("Location cannot be blank when provided")
            }
            if (subCategory.group != categoryGroup) {
                throw DomainValidationException(
                    "Sub category '${subCategory.persianName}' is not valid for category group '${categoryGroup.persianName}'"
                )
            }
        }
    }

    fun assignTo(workerId: UserId, userId: UserId): ServiceRequest {
        if (status != ServiceRequestStatus.APPROVED && status != ServiceRequestStatus.ASSIGNED) {
            throw DomainValidationException("Service request can only be assigned when it is approved")
        }
        return this.copy(
            assignedTo = workerId,
            status = ServiceRequestStatus.ASSIGNED,
            updatedAt = Instant.now(),
            updatedBy = userId
        )
    }

    fun approve(userId: UserId): ServiceRequest {
        if (status != ServiceRequestStatus.PENDING) {
            throw DomainValidationException("Service request can only be approved when it is pending")
        }
        return this.copy(
            status = ServiceRequestStatus.APPROVED,
            updatedAt = Instant.now(),
            updatedBy = userId
        )
    }

    fun startProgress(expectedCompletionAt: Instant? = null): ServiceRequest {
        if (status != ServiceRequestStatus.ASSIGNED) {
            throw DomainValidationException("Service request can only start progress when it is assigned")
        }
        return this.copy(
            status = ServiceRequestStatus.IN_PROGRESS,
            expectedCompletionAt = expectedCompletionAt,
            updatedAt = Instant.now()
        )
    }

    fun complete(
        userId: UserId,
        completionReport: String? = null,
        completionCost: Double? = null
    ): ServiceRequest {
        if (status != ServiceRequestStatus.IN_PROGRESS) {
            throw DomainValidationException("Service request can only be completed when it is in progress")
        }
        return this.copy(
            status = ServiceRequestStatus.COMPLETED,
            resolvedAt = Instant.now(),
            completionReport = completionReport?.takeIf { it.isNotBlank() },
            completionCost = completionCost?.takeIf { it >= 0 },
            updatedAt = Instant.now(),
            updatedBy = userId
        )
    }

    fun settle(userId: UserId): ServiceRequest {
        if (status != ServiceRequestStatus.COMPLETED) {
            throw DomainValidationException("Service request can only be settled when it is completed")
        }
        if (completionCost == null || completionCost <= 0.0) {
            throw DomainValidationException("Service request has no completion cost to settle")
        }
        if (assignedTo == null) {
            throw DomainValidationException("Service request has no assigned worker to pay")
        }
        return this.copy(
            status = ServiceRequestStatus.SETTLED,
            updatedAt = Instant.now(),
            updatedBy = userId
        )
    }

    fun reject(userId: UserId): ServiceRequest {
        if (status != ServiceRequestStatus.PENDING) {
            throw DomainValidationException("Service request can only be rejected while it is pending")
        }
        return this.copy(
            status = ServiceRequestStatus.REJECTED,
            updatedAt = Instant.now(),
            updatedBy = userId
        )
    }
}
