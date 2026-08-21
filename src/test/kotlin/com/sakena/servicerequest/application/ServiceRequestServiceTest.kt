package com.sakena.servicerequest.application

import com.sakena.property.domain.ApartmentRepository
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

    private val serviceRequestRepository = mockk<ServiceRequestRepository>()
    private val userRepository = mockk<UserRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val ratingService = mockk<com.sakena.rating.application.RatingService>(relaxed = true)
    private val service = ServiceRequestService(
        serviceRequestRepository,
        userRepository,
        residencyRepository,
        apartmentRepository,
        ratingService,
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
    fun `create rejects a resident with no active apartment`() {
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
    fun `confirmCompletionAndRate transitions a completed request to CONFIRMED`() {
        val resident = user(Role.RESIDENT)
        val worker = user(Role.STAFF)
        val request = serviceRequest(
            status = ServiceRequestStatus.COMPLETED,
            completionCost = null,
            createdBy = resident.id,
        ).copy(assignedTo = worker.id)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.confirmCompletionAndRate(
            ConfirmCompletionCommand(request.id, resident.id, score = 5),
        )

        assertEquals(ServiceRequestStatus.CONFIRMED, result.status)
    }

    @Test
    fun `confirmCompletionAndRate rejects a non-owner`() {
        val resident = user(Role.RESIDENT)
        val stranger = user(Role.RESIDENT)
        val request = serviceRequest(
            status = ServiceRequestStatus.COMPLETED,
            completionCost = null,
            createdBy = resident.id,
        )
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainForbiddenException> {
            service.confirmCompletionAndRate(
                ConfirmCompletionCommand(request.id, stranger.id, score = 5),
            )
        }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `rejectCompletion returns a completed request to IN_PROGRESS`() {
        val resident = user(Role.RESIDENT)
        val request = serviceRequest(
            status = ServiceRequestStatus.COMPLETED,
            completionCost = 250.0,
            createdBy = resident.id,
        )
        every { serviceRequestRepository.findById(request.id) } returns request
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.rejectCompletion(
            RejectCompletionCommand(request.id, resident.id),
        )

        assertEquals(ServiceRequestStatus.IN_PROGRESS, result.status)
        assertEquals(null, result.completionCost)
    }

    @Test
    fun `manager assigns an approved request to a staff member`() {
        val manager = user(Role.MANAGER)
        val worker = user(Role.STAFF)
        val apartment = Apartment.create(manager.managedBuildingId!!, "1", 1, BigDecimal("40"), 1)
        val request = serviceRequest(
            status = ServiceRequestStatus.APPROVED,
            completionCost = null,
            requestingApartmentId = apartment.id,
        )
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { userRepository.findById(manager.id) } returns manager
        every { userRepository.findById(worker.id) } returns worker
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.assignRequest(
            AssignServiceRequestCommand(request.id.value.toString(), worker.id, manager.id),
        )

        // The worker must end up on the request, or it never reaches their list.
        assertEquals(worker.id, result.assignedTo)
        assertEquals(ServiceRequestStatus.ASSIGNED, result.status)
    }

    @Test
    fun `staff from any building can be assigned, since they are a shared pool`() {
        val manager = user(Role.MANAGER)
        // A worker with no tie to this manager's building is still assignable:
        // service staff are contractors serving several buildings.
        val worker = user(Role.STAFF)
        val apartment = Apartment.create(manager.managedBuildingId!!, "1", 1, BigDecimal("40"), 1)
        val request = serviceRequest(
            status = ServiceRequestStatus.APPROVED,
            completionCost = null,
            requestingApartmentId = apartment.id,
        )
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { userRepository.findById(manager.id) } returns manager
        every { userRepository.findById(worker.id) } returns worker
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.assignRequest(
            AssignServiceRequestCommand(request.id.value.toString(), worker.id, manager.id),
        )

        assertEquals(worker.id, result.assignedTo)
    }

    @Test
    fun `a request cannot be assigned to a non-staff account`() {
        val manager = user(Role.MANAGER)
        val resident = user(Role.RESIDENT)
        val apartment = Apartment.create(manager.managedBuildingId!!, "1", 1, BigDecimal("40"), 1)
        val request = serviceRequest(
            status = ServiceRequestStatus.APPROVED,
            completionCost = null,
            requestingApartmentId = apartment.id,
        )
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { userRepository.findById(manager.id) } returns manager
        every { userRepository.findById(resident.id) } returns resident

        assertFailsWith<DomainValidationException> {
            service.assignRequest(
                AssignServiceRequestCommand(request.id.value.toString(), resident.id, manager.id),
            )
        }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `manager assigns cost responsibility to a request in the building they administer`() {
        val manager = user(Role.MANAGER)
        val apartmentId = ApartmentId.new()
        val apartment = Apartment.create(manager.managedBuildingId!!, "1", 1, BigDecimal("40"), 1)
        val request = serviceRequest(
            status = ServiceRequestStatus.CONFIRMED,
            completionCost = 250.0,
            requestingApartmentId = apartmentId,
        )
        every { userRepository.findById(manager.id) } returns manager
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { serviceRequestRepository.findById(request.id) } returns request
        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val result = service.assignCostResponsibility(command(request.id, manager.id))

        assertEquals(ServiceCostResponsibility.ALL_UNITS, result.costResponsibility)
    }

    @Test
    fun `manager cannot assign cost responsibility to a request in a building they do not administer`() {
        val manager = user(Role.MANAGER)
        val apartmentId = ApartmentId.new()
        val apartment = Apartment.create(BuildingId.new(), "1", 1, BigDecimal("40"), 1)
        val request = serviceRequest(
            status = ServiceRequestStatus.COMPLETED,
            completionCost = 250.0,
            requestingApartmentId = apartmentId,
        )
        every { userRepository.findById(manager.id) } returns manager
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainForbiddenException> {
            service.assignCostResponsibility(command(request.id, manager.id))
        }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
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
        val request = serviceRequest(
            status = ServiceRequestStatus.IN_PROGRESS,
            completionCost = 250.0,
            requestingApartmentId = null,
        )
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
        requestingApartmentId: ApartmentId? = ApartmentId.new(),
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
            requestingApartmentId = requestingApartmentId,
        )
    }
}
