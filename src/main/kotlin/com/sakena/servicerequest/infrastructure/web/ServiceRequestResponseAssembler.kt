package com.sakena.servicerequest.infrastructure.web

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.servicerequest.domain.ServiceRequest
import org.springframework.stereotype.Component

/** Builds service-request responses and enriches them with requesting-unit details. */
@Component
class ServiceRequestResponseAssembler(
    private val apartmentRepository: ApartmentRepository,
    private val buildingRepository: BuildingRepository,
) {

    fun toResponses(requests: List<ServiceRequest>): List<ServiceRequestResponse> =
        requests.map(::toResponse)

    fun toResponse(request: ServiceRequest): ServiceRequestResponse =
        ServiceRequestResponse.fromDomain(request, requestingUnitOf(request))

    private fun requestingUnitOf(request: ServiceRequest): RequestingUnitResponse? {
        val apartmentId = request.requestingApartmentId ?: return null
        val apartment = apartmentRepository.findById(apartmentId) ?: return null
        val building = buildingRepository.findById(apartment.buildingId) ?: return null
        return RequestingUnitResponse(
            unitNumber = apartment.unitNumber,
            floorNumber = apartment.floorNumber,
            buildingName = building.name,
        )
    }
}
