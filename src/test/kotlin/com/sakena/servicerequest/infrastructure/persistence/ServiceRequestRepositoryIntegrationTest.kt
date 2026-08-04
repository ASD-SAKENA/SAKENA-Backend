package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.IntegrationTest
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.Building
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ServiceRequestRepositoryIntegrationTest(
    @Autowired private val repository: ServiceRequestRepository,
    @Autowired private val buildingRepository: BuildingRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
) : IntegrationTest() {

    @ParameterizedTest
    @EnumSource(ServiceCostResponsibility::class)
    fun `cost responsibility survives a database round trip`(
        responsibility: ServiceCostResponsibility,
    ) {
        val request = completedRequest(responsibility)

        repository.save(request)
        val reloaded = repository.findById(request.id)

        assertEquals(responsibility, reloaded?.costResponsibility)
    }

    @Test
    fun `missing cost responsibility survives a database round trip`() {
        val request = completedRequest(costResponsibility = null)

        repository.save(request)
        val reloaded = repository.findById(request.id)

        assertNull(reloaded?.costResponsibility)
        assertNull(reloaded?.requestingApartmentId)
    }

    @Test
    fun `requesting apartment survives a database round trip`() {
        val building = buildingRepository.save(
            Building.create("Snapshot building", "Snapshot address"),
        )
        val apartment = apartmentRepository.save(
            Apartment.create(
                buildingId = building.id,
                unitNumber = "SNAPSHOT-${UUID.randomUUID().toString().take(8)}",
                floorNumber = 2,
                areaSquareMeters = BigDecimal("85.5"),
                bedrooms = 2,
            ),
        )
        val request = completedRequest(
            costResponsibility = ServiceCostResponsibility.REQUESTING_UNIT,
            requestingApartmentId = apartment.id,
        )

        repository.save(request)
        val reloaded = repository.findById(request.id)

        assertEquals(apartment.id, reloaded?.requestingApartmentId)
    }

    private fun completedRequest(
        costResponsibility: ServiceCostResponsibility?,
        requestingApartmentId: ApartmentId? = null,
    ): ServiceRequest {
        val createdBy = UserId(UUID.randomUUID())
        val completedAt = Instant.parse("2026-01-15T10:00:00Z")
        return ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = "Repair water pump",
            description = "The main water pump needs repair",
            location = "Basement",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
            createdBy = createdBy,
            updatedBy = createdBy,
            createdAt = completedAt.minusSeconds(3600),
            updatedAt = completedAt,
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = UserId(UUID.randomUUID()),
            resolvedAt = completedAt,
            completionReport = "Water pump repaired",
            completionCost = 250.0,
            costResponsibility = costResponsibility,
            requestingApartmentId = requestingApartmentId,
        )
    }
}
