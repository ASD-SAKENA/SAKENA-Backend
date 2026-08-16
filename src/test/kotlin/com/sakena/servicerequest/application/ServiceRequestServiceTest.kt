package com.sakena.servicerequest.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
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
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceRequestServiceTest {

    private val buildingId = BuildingId.new()
    private val serviceRequestRepository = mockk<ServiceRequestRepository>()
    private val userRepository = mockk<UserRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val buildingAccess = mockk<BuildingAccess>(relaxed = true)
    private val service = ServiceRequestService(
        serviceRequestRepository,
        userRepository,
        residencyRepository,
        apartmentRepository,
        buildingAccess,
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
    fun `create rejects a user without an active building residency`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByResident(resident.id) } returns null

        assertFailsWith<DomainForbiddenException> {
            service.create(createCommand(), resident.id)
        }

        verify(exactly = 0) { serviceRequestRepository.save(any()) }
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
    fun `manager list delegates filtering with only managed building apartments`() {
        val manager = user(Role.MANAGER)
        val unit = apartment(ApartmentId.new())
        val filters = com.sakena.servicerequest.domain.ServiceRequestFilters(
            status = ServiceRequestStatus.PENDING,
        )
        val request = serviceRequest(ServiceRequestStatus.PENDING, null)
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { apartmentRepository.findAllByBuildingId(buildingId) } returns listOf(unit)
        every {
            serviceRequestRepository.findAllByApartmentIdsAndFilters(setOf(unit.id), filters)
        } returns listOf(request)

        val result = service.getManagerRequests(filters, manager.id)

        assertEquals(listOf(request), result)
        verify(exactly = 0) { serviceRequestRepository.findAllByFilters(any()) }
    }

    @Test
    fun `resident list always binds the authenticated resident identity`() {
        val residentId = UserId.generate()
        val forgedId = UserId.generate()
        val supplied = com.sakena.servicerequest.domain.ServiceRequestFilters(createdBy = forgedId)
        val scoped = supplied.copy(createdBy = residentId)
        every { serviceRequestRepository.findAllByFilters(scoped) } returns emptyList()

        service.getResidentRequests(supplied, residentId)

        verify(exactly = 1) { serviceRequestRepository.findAllByFilters(scoped) }
        verify(exactly = 0) { serviceRequestRepository.findAllByFilters(supplied) }
    }

    @Test
    fun `assigned list always binds the authenticated staff identity`() {
        val staffId = UserId.generate()
        val forgedId = UserId.generate()
        val supplied = com.sakena.servicerequest.domain.ServiceRequestFilters(assignedTo = forgedId)
        val scoped = supplied.copy(assignedTo = staffId)
        every { serviceRequestRepository.findAllByFilters(scoped) } returns emptyList()

        service.getAssignedRequests(supplied, staffId)

        verify(exactly = 1) { serviceRequestRepository.findAllByFilters(scoped) }
        verify(exactly = 0) { serviceRequestRepository.findAllByFilters(supplied) }
    }

    @Test
    fun `manager cannot approve a request from another building`() {
        val manager = user(Role.MANAGER)
        val request = serviceRequest(ServiceRequestStatus.PENDING, null)
        val unit = apartment(request.requestingApartmentId!!)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(unit.id) } returns unit
        every {
            buildingAccess.requireManagerAccess(buildingId, manager.id)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.approveRequest(ApproveServiceRequestCommand(request.id, manager.id))
        }

        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `manager cannot assign staff from another building`() {
        val manager = user(Role.MANAGER)
        val staff = user(Role.STAFF)
        val request = serviceRequest(ServiceRequestStatus.APPROVED, null)
        val unit = apartment(request.requestingApartmentId!!)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(unit.id) } returns unit
        every { userRepository.findById(staff.id) } returns staff
        every {
            buildingAccess.requireStaffAccess(buildingId, staff.id)
        } throws DomainForbiddenException("You do not manage this staff member")

        assertFailsWith<DomainForbiddenException> {
            service.assignRequest(
                AssignServiceRequestCommand(
                    serviceRequestId = request.id.value.toString(),
                    workerId = staff.id,
                    userId = manager.id,
                ),
            )
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
        every { apartmentRepository.findById(request.requestingApartmentId!!) } returns
            apartment(request.requestingApartmentId!!)
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
        every { apartmentRepository.findById(request.requestingApartmentId!!) } returns
            apartment(request.requestingApartmentId!!)

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

    private fun apartment(id: ApartmentId): Apartment = Apartment.reconstitute(
        id = id,
        buildingId = buildingId,
        unitNumber = "101",
        floorNumber = 1,
        areaSquareMeters = BigDecimal("80.00"),
        bedrooms = 2,
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
    )
}
