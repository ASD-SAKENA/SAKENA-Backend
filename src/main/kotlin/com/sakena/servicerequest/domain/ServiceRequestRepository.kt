package com.sakena.servicerequest.domain

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import java.time.Instant

interface ServiceRequestRepository {
    fun save(request: ServiceRequest): ServiceRequest
    fun findById(id: ServiceRequestId): ServiceRequest?
    fun findAllByCreatedBy(userId: UserId): List<ServiceRequest>
    fun findAll(): List<ServiceRequest> // optional, for manager dashboard

    fun findAllByFilters(
        status: ServiceRequestStatus? = null,
        categoryGroup: ServiceCategoryGroup? = null,
        subCategory: ServiceSubCategory? = null,
        createdFrom: Instant? = null,
        createdTo: Instant? = null,
        updatedFrom: Instant? = null,
        updatedTo: Instant? = null
    ): List<ServiceRequest>
}
