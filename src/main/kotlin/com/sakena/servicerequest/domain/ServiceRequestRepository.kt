package com.sakena.servicerequest.domain

import com.sakena.user.domain.UserId

interface ServiceRequestRepository {
    fun save(request: ServiceRequest): ServiceRequest
    fun findById(id: ServiceRequestId): ServiceRequest?
    fun findAllByCreatedBy(userId: UserId): List<ServiceRequest>
    fun findAll(): List<ServiceRequest> // optional, for manager dashboard
}
