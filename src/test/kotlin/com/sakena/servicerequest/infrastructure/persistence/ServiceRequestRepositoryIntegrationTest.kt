package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.IntegrationTest
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
import java.time.Instant
import java.util.UUID

class ServiceRequestRepositoryIntegrationTest(
    @Autowired private val repository: ServiceRequestRepository,
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
    }

    private fun completedRequest(
        costResponsibility: ServiceCostResponsibility?,
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
        )
    }
}
