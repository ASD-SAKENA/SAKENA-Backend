package com.sakena.servicerequest.domain

import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ServiceRequestTest {

    private val testUserId = UserId.generate()
    private val now = Instant.now()

    private fun createTestRequest(
        status: ServiceRequestStatus = ServiceRequestStatus.PENDING,
        assignedTo: UserId? = null,
        resolvedAt: Instant? = null,
        completionCost: Double? = null,
        costResponsibility: ServiceCostResponsibility? = null,
        requestingApartmentId: ApartmentId? = null,
    ): ServiceRequest {
        return ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = "Test Request",
            description = "Test Description",
            location = "Building A, Floor 2",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR,
            createdBy = testUserId,
            updatedBy = testUserId,
            createdAt = now,
            updatedAt = now,
            status = status,
            assignedTo = assignedTo,
            resolvedAt = resolvedAt,
            completionCost = completionCost,
            costResponsibility = costResponsibility,
            requestingApartmentId = requestingApartmentId,
        )
    }

    // --- Creation Tests ---
    @Test
    fun `create should create a valid service request with PENDING status`() {
        val request = ServiceRequest.create(
            title = "Broken Elevator",
            description = "The elevator on floor 3 is not working",
            location = "Building A, Elevator 2",
            createdBy = testUserId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR
        )

        assertEquals("Broken Elevator", request.title)
        assertEquals("The elevator on floor 3 is not working", request.description)
        assertEquals("Building A, Elevator 2", request.location)
        assertEquals(testUserId, request.createdBy)
        assertEquals(ServiceRequestStatus.PENDING, request.status)
        assertNotNull(request.id)
        assertNotNull(request.createdAt)
        assertNotNull(request.updatedAt)
        assertNull(request.assignedTo)
        assertNull(request.resolvedAt)
        assertNull(request.expectedCompletionAt)
        assertNull(request.completionReport)
        assertNull(request.completionCost)
        assertNull(request.costResponsibility)
        assertNull(request.requestingApartmentId)
        assertTrue(request.createdAt <= Instant.now())
    }

    @Test
    fun `create should trim title and description`() {
        val request = ServiceRequest.create(
            title = "  Broken Elevator  ",
            description = "  The elevator is broken  ",
            location = "  Building A  ",
            createdBy = testUserId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR
        )

        assertEquals("Broken Elevator", request.title)
        assertEquals("The elevator is broken", request.description)
        assertEquals("Building A", request.location)
    }

    @Test
    fun `create should keep createdBy and set updatedBy to the creator`() {
        val request = ServiceRequest.create(
            title = "Broken Elevator",
            description = "The elevator is broken",
            location = null,
            createdBy = testUserId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR
        )

        assertEquals(testUserId, request.createdBy)
        assertEquals(testUserId, request.updatedBy)
    }

    @Test
    fun `create should allow null location`() {
        val request = ServiceRequest.create(
            title = "Broken Elevator",
            description = "The elevator is broken",
            location = null,
            createdBy = testUserId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR
        )

        assertNull(request.location)
    }

    @Test
    fun `create should fail if title is blank`() {
        assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "",
                description = "Some description",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.ELEVATOR
            )
        }

        assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "   ",
                description = "Some description",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.ELEVATOR
            )
        }
    }

    @Test
    fun `create should fail if description is blank`() {
        assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "Broken Elevator",
                description = "",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.ELEVATOR
            )
        }

        assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "Broken Elevator",
                description = "   ",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.ELEVATOR
            )
        }
    }

    @Test
    fun `create should fail when subcategory does not belong to the selected category group`() {
        val exception = assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "Broken Elevator",
                description = "The elevator is broken",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.GARDEN
            )
        }

        assertTrue(exception.message?.contains("not valid") == true)
    }

    @Test
    fun `create should allow subcategory for the matching category group`() {
        val request = ServiceRequest.create(
            title = "Broken Elevator",
            description = "The elevator is broken",
            location = null,
            createdBy = testUserId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR
        )

        assertEquals(ServiceCategoryGroup.FACILITIES, request.categoryGroup)
        assertEquals(ServiceSubCategory.ELEVATOR, request.subCategory)
    }

    @Test
    fun `create should fail when title is blank after trimming`() {
        val exception = assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "   ",
                description = "Valid description",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )
        }

        assertEquals("Title is required", exception.message)
    }

    @Test
    fun `create should fail when description is blank after trimming`() {
        val exception = assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "Valid title",
                description = "   ",
                location = null,
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )
        }

        assertEquals("Description is required", exception.message)
    }

    @Test
    fun `create should fail when location is blank when provided`() {
        val exception = assertThrows<DomainValidationException> {
            ServiceRequest.create(
                title = "Valid title",
                description = "Valid description",
                location = "   ",
                createdBy = testUserId,
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )
        }

        assertEquals("Location cannot be blank when provided", exception.message)
    }

    // --- Update Tests ---
    @Test
    fun `updateDetails should change content while PENDING and set updatedBy`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        val editorId = UserId.generate()

        val updated = request.updateDetails(
            title = "  Updated title  ",
            description = "  Updated description  ",
            location = "  New location  ",
            categoryGroup = ServiceCategoryGroup.GENERAL,
            subCategory = ServiceSubCategory.GENERAL,
            userId = editorId,
        )

        assertEquals("Updated title", updated.title)
        assertEquals("Updated description", updated.description)
        assertEquals("New location", updated.location)
        assertEquals(ServiceCategoryGroup.GENERAL, updated.categoryGroup)
        assertEquals(ServiceSubCategory.GENERAL, updated.subCategory)
        assertEquals(editorId, updated.updatedBy)
        assertEquals(ServiceRequestStatus.PENDING, updated.status)
        assertTrue(updated.updatedAt >= request.updatedAt)
    }

    @Test
    fun `updateDetails should fail when status is not PENDING`() {
        val nonPendingStatuses = ServiceRequestStatus.entries - ServiceRequestStatus.PENDING

        nonPendingStatuses.forEach { status ->
            val request = createTestRequest(status = status)

            assertThrows<DomainValidationException> {
                request.updateDetails(
                    title = "Updated title",
                    description = "Updated description",
                    location = null,
                    categoryGroup = ServiceCategoryGroup.GENERAL,
                    subCategory = ServiceSubCategory.GENERAL,
                    userId = UserId.generate(),
                )
            }
        }
    }

    @Test
    fun `updateDetails should validate the new content`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)

        assertThrows<DomainValidationException> {
            request.updateDetails(
                title = "   ",
                description = "Valid description",
                location = null,
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL,
                userId = UserId.generate(),
            )
        }

        assertThrows<DomainValidationException> {
            request.updateDetails(
                title = "Valid title",
                description = "Valid description",
                location = null,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.GARDEN,
                userId = UserId.generate(),
            )
        }
    }

    // --- Status Transition Tests ---
    @Test
    fun `approve should change status from PENDING to APPROVED`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        val adminId = UserId.generate()

        val updated = request.approve(adminId)

        assertEquals(ServiceRequestStatus.APPROVED, updated.status)
        assertEquals(adminId, updated.updatedBy)
        assertTrue(updated.updatedAt >= request.updatedAt)
        assertNull(updated.assignedTo)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `approve should fail if status is not PENDING`() {
        val request = createTestRequest(status = ServiceRequestStatus.APPROVED)
        assertThrows<DomainValidationException> {
            request.approve(UserId.generate())
        }

        val request2 = createTestRequest(status = ServiceRequestStatus.IN_PROGRESS)
        assertThrows<DomainValidationException> {
            request2.approve(UserId.generate())
        }
    }

    @Test
    fun `assignTo should change status to ASSIGNED and set assignedTo and updatedBy`() {
        val request = createTestRequest(status = ServiceRequestStatus.APPROVED)
        val workerId = UserId.generate()
        val adminId = UserId.generate()

        val updated = request.assignTo(workerId, adminId)

        assertEquals(ServiceRequestStatus.ASSIGNED, updated.status)
        assertEquals(workerId, updated.assignedTo)
        assertEquals(adminId, updated.updatedBy)
        assertTrue(updated.updatedAt >= request.updatedAt)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `startProgress should change status to IN_PROGRESS and set expectedCompletionAt`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.ASSIGNED,
            assignedTo = workerId
        )
        val expectedAt = Instant.now().plusSeconds(86400)

        val updated = request.startProgress(expectedCompletionAt = expectedAt)

        assertEquals(ServiceRequestStatus.IN_PROGRESS, updated.status)
        assertEquals(workerId, updated.assignedTo)
        assertEquals(expectedAt, updated.expectedCompletionAt)
        assertTrue(updated.updatedAt >= request.updatedAt)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `startProgress should allow null expectedCompletionAt`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.ASSIGNED,
            assignedTo = workerId
        )

        val updated = request.startProgress()

        assertEquals(ServiceRequestStatus.IN_PROGRESS, updated.status)
        assertNull(updated.expectedCompletionAt)
    }

    @Test
    fun `startProgress should fail if status is not ASSIGNED`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        assertThrows<DomainValidationException> {
            request.startProgress()
        }

        val request2 = createTestRequest(status = ServiceRequestStatus.IN_PROGRESS)
        assertThrows<DomainValidationException> {
            request2.startProgress()
        }
    }

    @Test
    fun `complete should change status to COMPLETED and set report and cost`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.IN_PROGRESS,
            assignedTo = workerId
        )

        Thread.sleep(1) // ensure time difference
        val userId = UserId.generate()
        val updated = request.complete(
            userId = userId,
            completionReport = "Fixed the elevator motor",
            completionCost = 250.0
        )

        assertEquals(ServiceRequestStatus.COMPLETED, updated.status)
        assertEquals(workerId, updated.assignedTo)
        assertEquals(userId, updated.updatedBy)
        assertNotNull(updated.resolvedAt)
        assertEquals("Fixed the elevator motor", updated.completionReport)
        assertEquals(250.0, updated.completionCost)
        assertTrue(updated.resolvedAt!! > request.createdAt)
        assertTrue(updated.updatedAt >= request.updatedAt)
    }

    @Test
    fun `complete should allow null report and cost`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.IN_PROGRESS,
            assignedTo = workerId
        )

        val updated = request.complete(userId = UserId.generate())

        assertEquals(ServiceRequestStatus.COMPLETED, updated.status)
        assertNull(updated.completionReport)
        assertNull(updated.completionCost)
    }

    @Test
    fun `complete should ignore blank report and negative cost`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.IN_PROGRESS,
            assignedTo = workerId
        )

        val updated = request.complete(
            userId = UserId.generate(),
            completionReport = "   ",
            completionCost = -10.0
        )

        assertNull(updated.completionReport)
        assertNull(updated.completionCost)
    }

    @Test
    fun `complete should fail if status is not IN_PROGRESS`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        assertThrows<DomainValidationException> {
            request.complete(userId = UserId.generate())
        }

        val request2 = createTestRequest(status = ServiceRequestStatus.APPROVED)
        assertThrows<DomainValidationException> {
            request2.complete(userId = UserId.generate())
        }
    }

    @Test
    fun `confirmCompletion should change status to CONFIRMED when the creator confirms`() {
        val request = createTestRequest(status = ServiceRequestStatus.COMPLETED)

        val confirmed = request.confirmCompletion(testUserId)

        assertEquals(ServiceRequestStatus.CONFIRMED, confirmed.status)
        assertEquals(testUserId, confirmed.updatedBy)
        assertTrue(confirmed.updatedAt > request.updatedAt)
    }

    @Test
    fun `confirmCompletion should fail if status is not COMPLETED`() {
        val nonCompletedStatuses = ServiceRequestStatus.entries - ServiceRequestStatus.COMPLETED

        nonCompletedStatuses.forEach { status ->
            val request = createTestRequest(status = status)

            assertThrows<DomainValidationException> {
                request.confirmCompletion(testUserId)
            }
        }
    }

    @Test
    fun `confirmCompletion should fail when confirmed by someone other than the creator`() {
        val request = createTestRequest(status = ServiceRequestStatus.COMPLETED)
        val neighbour = UserId.generate()

        assertThrows<com.sakena.shared.domain.DomainForbiddenException> {
            request.confirmCompletion(neighbour)
        }
    }

    @Test
    fun `rejectCompletion should return status to IN_PROGRESS and clear completion fields`() {
        val request = createTestRequest(
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = UserId.generate(),
            completionCost = 250.0,
        ).let {
            // completionReport/resolvedAt are private-setter fields reachable only via
            // reconstitute/complete; simplest path is via complete() on an IN_PROGRESS request.
            createTestRequest(status = ServiceRequestStatus.IN_PROGRESS, assignedTo = it.assignedTo!!)
                .complete(it.assignedTo!!, "Fixed the leak", 250.0)
        }

        val rejected = request.rejectCompletion(testUserId)

        assertEquals(ServiceRequestStatus.IN_PROGRESS, rejected.status)
        assertNull(rejected.completionReport)
        assertNull(rejected.completionCost)
        assertNull(rejected.resolvedAt)
        assertEquals(testUserId, rejected.updatedBy)
    }

    @Test
    fun `rejectCompletion should fail if status is not COMPLETED`() {
        val nonCompletedStatuses = ServiceRequestStatus.entries - ServiceRequestStatus.COMPLETED

        nonCompletedStatuses.forEach { status ->
            val request = createTestRequest(status = status)

            assertThrows<DomainValidationException> {
                request.rejectCompletion(testUserId)
            }
        }
    }

    @Test
    fun `rejectCompletion should fail when rejected by someone other than the creator`() {
        val request = createTestRequest(status = ServiceRequestStatus.COMPLETED)
        val neighbour = UserId.generate()

        assertThrows<com.sakena.shared.domain.DomainForbiddenException> {
            request.rejectCompletion(neighbour)
        }
    }

    @Test
    fun `settle and assignCostResponsibility should require CONFIRMED when a requesting apartment exists`() {
        val request = createTestRequest(
            status = ServiceRequestStatus.COMPLETED,
            completionCost = 250.0,
            requestingApartmentId = ApartmentId.new(),
        )

        assertThrows<DomainValidationException> {
            request.assignCostResponsibility(ServiceCostResponsibility.BUILDING_WALLET, UserId.generate())
        }
    }

    @Test
    fun `settle should require CONFIRMED before paying out when a requesting apartment exists`() {
        val request = createTestRequest(
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = UserId.generate(),
            completionCost = 250.0,
            costResponsibility = ServiceCostResponsibility.BUILDING_WALLET,
            requestingApartmentId = ApartmentId.new(),
        )

        assertThrows<DomainValidationException> {
            request.settle(UserId.generate())
        }
    }

    @Test
    fun `settle should succeed directly from COMPLETED when there is no requesting apartment`() {
        val request = createTestRequest(
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = UserId.generate(),
            completionCost = 250.0,
            costResponsibility = ServiceCostResponsibility.BUILDING_WALLET,
            requestingApartmentId = null,
        )

        val settled = request.settle(UserId.generate())

        assertEquals(ServiceRequestStatus.SETTLED, settled.status)
    }

    @Test
    fun `assignCostResponsibility and settle should succeed from CONFIRMED`() {
        val confirmed = createTestRequest(
            status = ServiceRequestStatus.CONFIRMED,
            assignedTo = UserId.generate(),
            completionCost = 250.0,
            requestingApartmentId = ApartmentId.new(),
        )

        val withResponsibility = confirmed.assignCostResponsibility(
            ServiceCostResponsibility.BUILDING_WALLET,
            UserId.generate(),
        )
        val settled = withResponsibility.settle(UserId.generate())

        assertEquals(ServiceRequestStatus.SETTLED, settled.status)
    }

    @Test
    fun `assignCostResponsibility should record responsibility and manager`() {
        val managerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.CONFIRMED,
            completionCost = 250.0,
            requestingApartmentId = ApartmentId.new(),
        )

        val updated = request.assignCostResponsibility(
            ServiceCostResponsibility.ALL_UNITS,
            managerId,
        )

        assertEquals(ServiceCostResponsibility.ALL_UNITS, updated.costResponsibility)
        assertEquals(managerId, updated.updatedBy)
        assertFalse(updated.updatedAt.isBefore(request.updatedAt))
    }

    @Test
    fun `assignCostResponsibility should allow changing responsibility before settlement`() {
        val managerId = UserId.generate()
        val apartmentId = ApartmentId.new()
        val request = createTestRequest(
            status = ServiceRequestStatus.CONFIRMED,
            completionCost = 250.0,
            costResponsibility = ServiceCostResponsibility.ALL_UNITS,
            requestingApartmentId = apartmentId,
        )

        val updated = request.assignCostResponsibility(
            ServiceCostResponsibility.REQUESTING_UNIT,
            managerId,
        )

        assertEquals(ServiceCostResponsibility.REQUESTING_UNIT, updated.costResponsibility)
        assertEquals(apartmentId, updated.requestingApartmentId)
    }

    @Test
    fun `assignCostResponsibility should require a completed request`() {
        val managerId = UserId.generate()
        val invalidStatuses = ServiceRequestStatus.entries - ServiceRequestStatus.COMPLETED - ServiceRequestStatus.CONFIRMED

        invalidStatuses.forEach { status ->
            val request = createTestRequest(status = status, completionCost = 250.0)

            assertThrows<DomainValidationException> {
                request.assignCostResponsibility(ServiceCostResponsibility.BUILDING_WALLET, managerId)
            }
        }
    }

    @Test
    fun `assignCostResponsibility should require a positive completion cost`() {
        listOf(null, 0.0, -1.0).forEach { cost ->
            val request = createTestRequest(
                status = ServiceRequestStatus.COMPLETED,
                completionCost = cost,
            )

            assertThrows<DomainValidationException> {
                request.assignCostResponsibility(
                    ServiceCostResponsibility.BUILDING_WALLET,
                    UserId.generate(),
                )
            }
        }
    }

    @Test
    fun `assignCostResponsibility should require a requesting apartment for billable targets`() {
        listOf(
            ServiceCostResponsibility.ALL_UNITS,
            ServiceCostResponsibility.REQUESTING_UNIT,
        ).forEach { responsibility ->
            val request = createTestRequest(
                status = ServiceRequestStatus.COMPLETED,
                completionCost = 250.0,
                requestingApartmentId = null,
            )

            val exception = assertThrows<DomainValidationException> {
                request.assignCostResponsibility(
                    responsibility,
                    UserId.generate(),
                )
            }

            assertEquals(
                "Billable cost responsibility requires a requesting apartment",
                exception.message,
            )
        }
    }

    @Test
    fun `settle should require an assigned cost responsibility`() {
        val request = createTestRequest(
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = UserId.generate(),
            completionCost = 250.0,
        )

        val exception = assertThrows<DomainValidationException> {
            request.settle(UserId.generate())
        }

        assertEquals("Service request cost responsibility has not been assigned", exception.message)
    }

    @Test
    fun `settle should mark a financially prepared request as settled`() {
        val managerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = UserId.generate(),
            completionCost = 250.0,
            costResponsibility = ServiceCostResponsibility.BUILDING_WALLET,
        )

        val settled = request.settle(managerId)

        assertEquals(ServiceRequestStatus.SETTLED, settled.status)
        assertEquals(managerId, settled.updatedBy)
    }

    @Test
    fun `reject should change status to REJECTED from PENDING and set updatedBy`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        val adminId = UserId.generate()

        val updated = request.reject(adminId)

        assertEquals(ServiceRequestStatus.REJECTED, updated.status)
        assertEquals(adminId, updated.updatedBy)
        assertTrue(updated.updatedAt >= request.updatedAt)
        assertNull(updated.assignedTo)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `reject should fail if status is not PENDING`() {
        val request = createTestRequest(status = ServiceRequestStatus.APPROVED)
        assertThrows<DomainValidationException> {
            request.reject(UserId.generate())
        }

        val request2 = createTestRequest(status = ServiceRequestStatus.IN_PROGRESS)
        assertThrows<DomainValidationException> {
            request2.reject(UserId.generate())
        }
    }

    // --- Full lifecycle test ---
    @Test
    fun `full lifecycle from creation to completion`() {
        val residentId = UserId.generate()
        val managerId = UserId.generate()
        val workerId = UserId.generate()

        val request = ServiceRequest.create(
            title = "Fix AC",
            description = "AC unit not cooling",
            location = "Room 101",
            createdBy = residentId,
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.HVAC
        )

        assertEquals(ServiceRequestStatus.PENDING, request.status)
        assertEquals(residentId, request.createdBy)
        assertEquals(residentId, request.updatedBy)
        assertNull(request.expectedCompletionAt)
        assertNull(request.completionReport)
        assertNull(request.completionCost)

        val approved = request.approve(managerId)
        assertEquals(ServiceRequestStatus.APPROVED, approved.status)
        assertEquals(managerId, approved.updatedBy)

        val assigned = approved.assignTo(workerId, managerId)
        assertEquals(ServiceRequestStatus.ASSIGNED, assigned.status)
        assertEquals(workerId, assigned.assignedTo)
        assertEquals(managerId, assigned.updatedBy)

        val expectedAt = Instant.now().plusSeconds(7200)
        val inProgress = assigned.startProgress(expectedCompletionAt = expectedAt)
        assertEquals(ServiceRequestStatus.IN_PROGRESS, inProgress.status)
        assertEquals(expectedAt, inProgress.expectedCompletionAt)

        val completed = inProgress.complete(
            userId = workerId,
            completionReport = "Replaced the compressor",
            completionCost = 350.0
        )
        assertEquals(ServiceRequestStatus.COMPLETED, completed.status)
        assertEquals(workerId, completed.updatedBy)
        assertNotNull(completed.resolvedAt)
        assertEquals("Replaced the compressor", completed.completionReport)
        assertEquals(350.0, completed.completionCost)
    }

    // --- Reconstitute Tests ---
    @Test
    fun `reconstitute should create a valid ServiceRequest from persistence`() {
        val id = ServiceRequestId.generate()
        val userId = UserId.generate()
        val workerId = UserId.generate()
        val createdAt = Instant.now().minusSeconds(3600)
        val updatedAt = Instant.now().minusSeconds(1800)
        val resolvedAt = Instant.now()
        val expectedAt = Instant.now().plusSeconds(86400)

        val request = ServiceRequest.reconstitute(
            id = id,
            title = "Test Title",
            description = "Test Description",
            location = "Test Location",
            categoryGroup = ServiceCategoryGroup.BUILDING,
            subCategory = ServiceSubCategory.DOOR_WINDOW,
            createdBy = userId,
            updatedBy = userId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = workerId,
            resolvedAt = resolvedAt,
            expectedCompletionAt = expectedAt,
            completionReport = "Fixed the door",
            completionCost = 120.0,
            costResponsibility = ServiceCostResponsibility.BUILDING_WALLET,
        )

        assertEquals(id, request.id)
        assertEquals("Test Title", request.title)
        assertEquals("Test Description", request.description)
        assertEquals("Test Location", request.location)
        assertEquals(userId, request.createdBy)
        assertEquals(userId, request.updatedBy)
        assertEquals(createdAt, request.createdAt)
        assertEquals(updatedAt, request.updatedAt)
        assertEquals(ServiceRequestStatus.COMPLETED, request.status)
        assertEquals(workerId, request.assignedTo)
        assertEquals(resolvedAt, request.resolvedAt)
        assertEquals(expectedAt, request.expectedCompletionAt)
        assertEquals("Fixed the door", request.completionReport)
        assertEquals(120.0, request.completionCost)
        assertEquals(ServiceCostResponsibility.BUILDING_WALLET, request.costResponsibility)
    }

    @Test
    fun `reconstitute should handle null optional fields`() {
        val request = ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = "Test",
            description = "Test",
            location = null,
            categoryGroup = ServiceCategoryGroup.GENERAL,
            subCategory = ServiceSubCategory.GENERAL,
            createdBy = testUserId,
            updatedBy = testUserId,
            createdAt = now,
            updatedAt = now,
            status = ServiceRequestStatus.PENDING,
            assignedTo = null,
            resolvedAt = null
        )

        assertNull(request.assignedTo)
        assertNull(request.resolvedAt)
        assertNull(request.expectedCompletionAt)
        assertNull(request.completionReport)
        assertNull(request.completionCost)
        assertNull(request.costResponsibility)
    }

    @Test
    fun `reconstitute should fail when title is blank`() {
        assertThrows<DomainValidationException> {
            ServiceRequest.reconstitute(
                id = ServiceRequestId.generate(),
                title = "",
                description = "Valid description",
                location = null,
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL,
                createdBy = testUserId,
                updatedBy = testUserId,
                createdAt = now,
                updatedAt = now,
                status = ServiceRequestStatus.PENDING,
                assignedTo = null,
                resolvedAt = null
            )
        }
    }
}
