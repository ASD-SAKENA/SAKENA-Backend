package com.sakena.servicerequest.infrastructure.web

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import java.time.Instant

data class ServiceRequestResponse(
    val id: String,
    val title: String,
    val description: String,
    val location: String?,
    val categoryGroup: ServiceCategoryGroup,
    val subCategory: ServiceSubCategory,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: ServiceRequestStatus,
    val assignedTo: String?,
    val resolvedAt: Instant?,
    val expectedCompletionAt: Instant?,
    val completionReport: String?,
    val completionCost: Double?
) {
    companion object {
        fun fromDomain(request: ServiceRequest): ServiceRequestResponse {
            return ServiceRequestResponse(
                id = request.id.value.toString(),
                title = request.title,
                description = request.description,
                location = request.location,
                categoryGroup = request.categoryGroup,
                subCategory = request.subCategory,
                createdBy = request.createdBy.value.toString(),
                updatedBy = request.updatedBy.value.toString(),
                createdAt = request.createdAt,
                updatedAt = request.updatedAt,
                status = request.status,
                assignedTo = request.assignedTo?.value?.toString(),
                resolvedAt = request.resolvedAt,
                expectedCompletionAt = request.expectedCompletionAt,
                completionReport = request.completionReport,
                completionCost = request.completionCost
            )
        }
    }
}
