package com.sakena.servicerequest.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.rating.application.RatingService
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestEvent
import com.sakena.servicerequest.domain.ServiceRequestEventRepository
import com.sakena.servicerequest.domain.ServiceRequestEventType
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ServiceRequestEventLoggingTest {

    private val serviceRequestRepository = mockk<ServiceRequestRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>(relaxed = true)
    private val ratingService = mockk<RatingService>(relaxed = true)
    private val eventRepository = mockk<ServiceRequestEventRepository>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val service = ServiceRequestService(
        serviceRequestRepository,
        userRepository,
        residencyRepository,
        apartmentRepository,
        ratingService,
        eventRepository,
        objectMapper = objectMapper,
    )

    @Test
    fun `create records created event`() {
        val residentId = UserId.generate()
        val user = User.register("u", "u@x.com", "password123", { it }, Role.RESIDENT)
        every { userRepository.findById(residentId) } returns user

        val apartmentId = ApartmentId.new()
        every { residencyRepository.findActiveByResident(residentId) } returns
            Residency.start(apartmentId, residentId, TenancyType.OWNER_OCCUPIER, Instant.now())

        every { serviceRequestRepository.save(any()) } answers { firstArg() }

        val cmd = CreateServiceRequestCommand(
            title = "fix sink",
            description = "kitchen sink leaking",
            location = "kitchen",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
        )

        val saved = service.create(cmd, residentId)

        verify { eventRepository.save(match { it.type == ServiceRequestEventType.CREATED && it.serviceRequestId == saved.id }) }
    }

    @Test
    fun `approve saves the changed request before recording its matching snapshot`() {
        val creatorId = UserId.generate()
        val managerId = UserId.generate()
        val request = ServiceRequest.create(
            title = "fix elevator",
            description = "elevator is stuck",
            location = "lobby",
            createdBy = creatorId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR,
        )
        val now = Instant.now()
        val manager = User.reconstitute(
            id = managerId,
            username = "manager",
            email = "manager@x.com",
            passwordHash = "hash",
            role = Role.MANAGER,
            createdAt = now,
            updatedAt = now,
            active = true,
            managedBuildingId = BuildingId.new(),
        )
        val eventSlot = slot<ServiceRequestEvent>()
        every { userRepository.findById(managerId) } returns manager
        every { serviceRequestRepository.findById(request.id) } returns request
        every { serviceRequestRepository.save(any()) } answers { firstArg() }
        every { eventRepository.save(capture(eventSlot)) } answers { firstArg() }

        val approved = service.approveRequest(ApproveServiceRequestCommand(request.id, managerId))

        val event = eventSlot.captured
        val payload = objectMapper.readTree(event.payload)
        assertEquals(ServiceRequestStatus.APPROVED, approved.status)
        assertEquals(ServiceRequestEventType.APPROVED, event.type)
        assertEquals(managerId, event.performedBy)
        assertEquals("status=APPROVED", payload["note"].asText())
        assertEquals("APPROVED", payload["request"]["status"].asText())
        verifyOrder {
            serviceRequestRepository.save(match { it.status == ServiceRequestStatus.APPROVED })
            eventRepository.save(match { it.type == ServiceRequestEventType.APPROVED })
        }
    }
}
