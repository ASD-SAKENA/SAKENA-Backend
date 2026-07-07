package com.sakena.servicerequest.domain

import com.sakena.user.domain.UserId

interface ServiceRequestRepository {
    fun save(request: ServiceRequest): ServiceRequest
    fun findById(id: ServiceRequestId): ServiceRequest?
    fun findAll(): List<ServiceRequest>

    fun findAllByFilters(filters: ServiceRequestFilters): List<ServiceRequest>
}
