package com.sakena.servicerequest.domain

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
        resolvedAt: Instant? = null
    ): ServiceRequest {
        return ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = "Test Request",
            description = "Test Description",
            location = "Building A, Floor 2",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELEVATOR,
            createdBy = testUserId,
            createdAt = now,
            updatedAt = now,
            status = status,
            assignedTo = assignedTo,
            resolvedAt = resolvedAt
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

    // --- Status Transition Tests ---
    @Test
    fun `assignTo should change status to APPROVED and set assignedTo`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        val workerId = UserId.generate()

        val updated = request.assignTo(workerId)

        assertEquals(ServiceRequestStatus.APPROVED, updated.status)
        assertEquals(workerId, updated.assignedTo)
        assertTrue(updated.updatedAt > request.updatedAt)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `startProgress should change status to IN_PROGRESS from APPROVED`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.APPROVED,
            assignedTo = workerId
        )

        val updated = request.startProgress()

        assertEquals(ServiceRequestStatus.IN_PROGRESS, updated.status)
        assertEquals(workerId, updated.assignedTo)
        assertTrue(updated.updatedAt > request.updatedAt)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `startProgress should fail if status is not APPROVED`() {
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
    fun `complete should change status to COMPLETED and set resolvedAt`() {
        val workerId = UserId.generate()
        val request = createTestRequest(
            status = ServiceRequestStatus.IN_PROGRESS,
            assignedTo = workerId
        )

        Thread.sleep(1) // ensure time difference
        val updated = request.complete()

        assertEquals(ServiceRequestStatus.COMPLETED, updated.status)
        assertEquals(workerId, updated.assignedTo)
        assertNotNull(updated.resolvedAt)
        assertTrue(updated.resolvedAt!! > request.createdAt)
        assertTrue(updated.updatedAt > request.updatedAt)
    }

    @Test
    fun `complete should fail if status is not IN_PROGRESS`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)
        assertThrows<DomainValidationException> {
            request.complete()
        }

        val request2 = createTestRequest(status = ServiceRequestStatus.APPROVED)
        assertThrows<DomainValidationException> {
            request2.complete()
        }
    }

    @Test
    fun `reject should change status to REJECTED from PENDING`() {
        val request = createTestRequest(status = ServiceRequestStatus.PENDING)

        val updated = request.reject()

        assertEquals(ServiceRequestStatus.REJECTED, updated.status)
        assertTrue(updated.updatedAt > request.updatedAt)
        assertNull(updated.assignedTo)
        assertNull(updated.resolvedAt)
    }

    @Test
    fun `reject should fail if status is not PENDING`() {
        val request = createTestRequest(status = ServiceRequestStatus.APPROVED)
        assertThrows<DomainValidationException> {
            request.reject()
        }

        val request2 = createTestRequest(status = ServiceRequestStatus.IN_PROGRESS)
        assertThrows<DomainValidationException> {
            request2.reject()
        }
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

        val request = ServiceRequest.reconstitute(
            id = id,
            title = "Test Title",
            description = "Test Description",
            location = "Test Location",
            categoryGroup = ServiceCategoryGroup.BUILDING,
            subCategory = ServiceSubCategory.DOOR_WINDOW,
            createdBy = userId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = ServiceRequestStatus.COMPLETED,
            assignedTo = workerId,
            resolvedAt = resolvedAt
        )

        assertEquals(id, request.id)
        assertEquals("Test Title", request.title)
        assertEquals("Test Description", request.description)
        assertEquals("Test Location", request.location)
        assertEquals(userId, request.createdBy)
        assertEquals(createdAt, request.createdAt)
        assertEquals(updatedAt, request.updatedAt)
        assertEquals(ServiceRequestStatus.COMPLETED, request.status)
        assertEquals(workerId, request.assignedTo)
        assertEquals(resolvedAt, request.resolvedAt)
    }

    @Test
    fun `reconstitute should handle null assignedTo and resolvedAt`() {
        val request = ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = "Test",
            description = "Test",
            location = null,
            categoryGroup = ServiceCategoryGroup.GENERAL,
            subCategory = ServiceSubCategory.GENERAL,
            createdBy = testUserId,
            createdAt = now,
            updatedAt = now,
            status = ServiceRequestStatus.PENDING,
            assignedTo = null,
            resolvedAt = null
        )

        assertNull(request.assignedTo)
        assertNull(request.resolvedAt)
    }
}
