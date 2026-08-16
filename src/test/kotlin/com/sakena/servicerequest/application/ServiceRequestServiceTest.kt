package com.sakena.servicerequest.application

import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
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
import com.sakena.property.domain.model.BuildingId
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
    private val residencyRepository = mockk<ResidencyRepository>()
    private val service = ServiceRequestService(
        serviceRequestRepository,
        userRepository,
        residencyRepository,
    )

    @Test
    fun `create snapshots the resident active apartment`() {
        val resident = user(Role.RESIDENT)
        val apartmentId = ApartmentId.new()
        val residency = Residency.start(
            apartmentId = apartmentId,
            residentId = resident.id,
            tenancy = TenancyType.TENANT,
        )
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByResident(resident.id) } returns residency
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.create(createCommand(), resident.id)

        assertEquals(apartmentId, result.requestingApartmentId)
        verify(exactly = 1) { serviceRequestRepository.save(result) }
    }

    @Test
    fun `create remains valid when the user has no active apartment`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByResident(resident.id) } returns null
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.create(createCommand(), resident.id)

        assertEquals(null, result.requestingApartmentId)
    }

    @Test
    fun `owner updates their pending service request`() {
        val resident = user(Role.RESIDENT)
        val request = serviceRequest(status = ServiceRequestStatus.PENDING, completionCost = null, createdBy = resident.id)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.updateRequest(updateCommand(request.id, resident.id))

        assertEquals("Updated title", result.title)
        assertEquals(resident.id, result.updatedBy)
        verify(exactly = 1) { serviceRequestRepository.save(result) }
    }

    @Test
    fun `non-owner cannot update another resident's service request`() {
        val owner = user(Role.RESIDENT)
        val impersonator = user(Role.RESIDENT)
        val request = serviceRequest(status = ServiceRequestStatus.PENDING, completionCost = null, createdBy = owner.id)
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainForbiddenException> {
            service.updateRequest(updateCommand(request.id, impersonator.id))
        }

        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `updateRequest fails for an unknown service request`() {
        val resident = user(Role.RESIDENT)
        val requestId = ServiceRequestId.generate()
        every { serviceRequestRepository.findById(requestId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.updateRequest(updateCommand(requestId, resident.id))
        }

        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

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

    private fun updateCommand(
        serviceRequestId: ServiceRequestId,
        userId: UserId,
    ) = UpdateServiceRequestCommand(
        serviceRequestId = serviceRequestId,
        title = "Updated title",
        description = "Updated description",
        location = "Basement",
        categoryGroup = ServiceCategoryGroup.FACILITIES,
        subCategory = ServiceSubCategory.PLUMBING,
        userId = userId,
    )

    private fun createCommand() = CreateServiceRequestCommand(
        title = "Repair water pump",
        description = "The main water pump needs repair",
        location = "Basement",
        categoryGroup = ServiceCategoryGroup.FACILITIES,
        subCategory = ServiceSubCategory.PLUMBING,
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
            managedBuildingId = if (role == Role.MANAGER) BuildingId.new() else null,
        )
    }

    private fun serviceRequest(
        status: ServiceRequestStatus,
        completionCost: Double?,
        createdBy: UserId = UserId.generate(),
    ): ServiceRequest {
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
            requestingApartmentId = ApartmentId.new(),
        )
    }
}
