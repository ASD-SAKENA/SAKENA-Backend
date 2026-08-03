package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceRequestServiceTest {

    private val serviceRequestRepository = mockk<ServiceRequestRepository>()
    private val userRepository = mockk<UserRepository>()
    private val service = ServiceRequestService(serviceRequestRepository, userRepository)

    @Test
    fun `manager assigns cost responsibility to a completed service request`() {
        val manager = user(Role.MANAGER)
        val request = serviceRequest(status = ServiceRequestStatus.COMPLETED, completionCost = 250.0)
        val command = command(request.id, manager.id)
        every { userRepository.findById(manager.id) } returns manager
        every { serviceRequestRepository.findById(request.id) } returns request
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.assignCostResponsibility(command)

        assertEquals(ServiceCostResponsibility.ALL_UNITS, result.costResponsibility)
        assertEquals(manager.id, result.updatedBy)
        verify(exactly = 1) { serviceRequestRepository.save(result) }
    }

    @Test
    fun `unknown acting user cannot assign cost responsibility`() {
        val unknownUserId = UserId.generate()
        val requestId = ServiceRequestId.generate()
        every { userRepository.findById(unknownUserId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.assignCostResponsibility(command(requestId, unknownUserId))
        }

        verify(exactly = 0) { serviceRequestRepository.findById(any()) }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `non-manager cannot assign cost responsibility`() {
        val resident = user(Role.RESIDENT)
        val requestId = ServiceRequestId.generate()
        every { userRepository.findById(resident.id) } returns resident

        assertFailsWith<DomainForbiddenException> {
            service.assignCostResponsibility(command(requestId, resident.id))
        }

        verify(exactly = 0) { serviceRequestRepository.findById(any()) }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `manager cannot assign cost responsibility to an unknown service request`() {
        val manager = user(Role.MANAGER)
        val requestId = ServiceRequestId.generate()
        every { userRepository.findById(manager.id) } returns manager
        every { serviceRequestRepository.findById(requestId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.assignCostResponsibility(command(requestId, manager.id))
        }

        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `domain validation failure is propagated without saving`() {
        val manager = user(Role.MANAGER)
        val request = serviceRequest(status = ServiceRequestStatus.IN_PROGRESS, completionCost = 250.0)
        every { userRepository.findById(manager.id) } returns manager
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainValidationException> {
            service.assignCostResponsibility(command(request.id, manager.id))
        }

        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    private fun command(
        serviceRequestId: ServiceRequestId,
        managerId: UserId,
    ) = AssignServiceCostResponsibilityCommand(
        serviceRequestId = serviceRequestId,
        responsibility = ServiceCostResponsibility.ALL_UNITS,
        managerId = managerId,
    )

    private fun user(role: Role): User {
        val now = Instant.parse("2026-01-15T10:00:00Z")
        return User.reconstitute(
            id = UserId.generate(),
            username = "${role.name.lowercase()}-user",
            email = "${role.name.lowercase()}@example.com",
            passwordHash = "hashed-password",
            role = role,
            createdAt = now,
            updatedAt = now,
            active = true,
        )
    }

    private fun serviceRequest(
        status: ServiceRequestStatus,
        completionCost: Double?,
    ): ServiceRequest {
        val createdBy = UserId.generate()
        val now = Instant.parse("2026-01-15T10:00:00Z")
        return ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = "Repair water pump",
            description = "The main water pump needs repair",
            location = "Basement",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
            createdBy = createdBy,
            updatedBy = createdBy,
            createdAt = now.minusSeconds(3600),
            updatedAt = now,
            status = status,
            assignedTo = UserId.generate(),
            resolvedAt = if (status == ServiceRequestStatus.COMPLETED) now else null,
            completionCost = completionCost,
        )
    }
}
