package com.sakena.servicerequest.domain

import com.sakena.property.domain.model.ApartmentId

interface ServiceRequestRepository {
    fun save(request: ServiceRequest): ServiceRequest
    fun findById(id: ServiceRequestId): ServiceRequest?

    fun findAllByApartmentIds(apartmentIds: Set<ApartmentId>): List<ServiceRequest>

    fun findAllByFilters(filters: ServiceRequestFilters): List<ServiceRequest>

    fun findAllByApartmentIdsAndFilters(
        apartmentIds: Set<ApartmentId>,
        filters: ServiceRequestFilters,
    ): List<ServiceRequest>
}
