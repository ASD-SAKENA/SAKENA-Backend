package com.sakena.servicerequest.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sakena.servicerequest.application.AssignServiceRequestCommand
import com.sakena.servicerequest.application.ApproveServiceRequestCommand
import com.sakena.servicerequest.application.CategoryGroupOptionResult
import com.sakena.servicerequest.application.CategoryOptionsResult
import com.sakena.servicerequest.application.CompleteServiceRequestCommand
import com.sakena.servicerequest.application.CreateServiceRequestCommand
import com.sakena.servicerequest.application.ServiceRequestService
import com.sakena.servicerequest.application.StartProgressCommand
import com.sakena.servicerequest.application.SubCategoryOptionResult
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestFilters
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class TestControllerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(e: MethodArgumentNotValidException): Map<String, Any> {
        val errors = e.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to it.defaultMessage)
        }
        return mapOf(
            "status" to HttpStatus.BAD_REQUEST.value(),
            "errors" to errors
        )
    }

    @ExceptionHandler(DomainValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleDomainValidationException(e: DomainValidationException): Map<String, String> {
        return mapOf("error" to (e.message ?: "Invalid request"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(e: IllegalArgumentException): Map<String, String> {
        return mapOf("error" to (e.message ?: "Invalid argument"))
    }

    @ExceptionHandler(RuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleRuntimeException(e: RuntimeException): Map<String, String> {
        e.printStackTrace()
        return mapOf("error" to (e.message ?: "Internal server error"))
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(e: Exception): Map<String, String> {
        e.printStackTrace()
        return mapOf("error" to (e.message ?: "Unknown error"))
    }
}

class ServiceRequestControllerTest {

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private val profileService: ProfileService = mockk()
    private val serviceRequestService: ServiceRequestService = mockk()
    private lateinit var controller: ServiceRequestController

    private val testUserId = UserId.generate()
    private val testUsername = "testuser"
    private lateinit var testUser: User

    private var previousAuthentication: Authentication? = null

    @BeforeEach
    fun setUp() {
        testUser = User.reconstitute(
            id = testUserId,
            username = testUsername,
            email = "test@example.com",
            passwordHash = "hashed_password",
            role = Role.RESIDENT,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )

        controller = ServiceRequestController(profileService, serviceRequestService)

        val validator = LocalValidatorFactoryBean()
        validator.afterPropertiesSet()

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(TestControllerAdvice())
            .setValidator(validator)
            .build()
    }

    private fun setupSecurityContext() {
        previousAuthentication = SecurityContextHolder.getContext().authentication
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val authentication = UsernamePasswordAuthenticationToken(testUsername, null, authorities)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
    }

    private fun cleanupSecurityContext() {
        if (previousAuthentication != null) {
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = previousAuthentication
            SecurityContextHolder.setContext(context)
        } else {
            SecurityContextHolder.clearContext()
        }
    }

    // ==================== CREATE ====================

    @Test
    fun `createRequest should return 201 Created when request is valid`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "The elevator is not working",
                location = "Building A, Floor 2",
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.ELEVATOR
            )

            val createdRequest = createMockServiceRequest(
                title = request.title!!,
                description = request.description!!,
                location = request.location!!,
                createdBy = testUserId,
                status = ServiceRequestStatus.PENDING,
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.ELEVATOR
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.create(
                    any<CreateServiceRequestCommand>(),
                    testUserId
                )
            } returns createdRequest

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(createdRequest.id.value.toString()))
                .andExpect(jsonPath("$.title").value(request.title))
                .andExpect(jsonPath("$.description").value(request.description))
                .andExpect(jsonPath("$.location").value(request.location))
                .andExpect(jsonPath("$.categoryGroup").value("FACILITIES"))
                .andExpect(jsonPath("$.subCategory").value("ELEVATOR"))
                .andExpect(jsonPath("$.status").value("PENDING"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `createRequest should return 400 when title is blank`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "",
                description = "The elevator is not working",
                location = "Building A, Floor 2",
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `createRequest should return 400 when description is blank`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "",
                location = "Building A, Floor 2",
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `createRequest should return 400 when location is blank`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "The elevator is not working",
                location = "   ",
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Location cannot be blank when provided"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `createRequest should return 400 when subcategory does not belong to the selected category group`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "The elevator is not working",
                location = "Building A",
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.GARDEN
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.create(
                    any<CreateServiceRequestCommand>(),
                    testUserId
                )
            } throws DomainValidationException("Sub category 'باغچه' is not valid for category group 'تاسیسات'")

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Sub category 'باغچه' is not valid for category group 'تاسیسات'"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `createRequest should return 500 error when user not found`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "The elevator is not working",
                location = "Building A, Floor 2",
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )

            every { profileService.getUserByUsername(testUsername) } returns null

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().is5xxServerError)
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== GET MY REQUESTS ====================

    @Test
    fun `getMyRequests should return list of user's requests`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("Request 1", "Desc 1", testUserId, ServiceRequestStatus.PENDING),
                createMockServiceRequest("Request 2", "Desc 2", testUserId, ServiceRequestStatus.IN_PROGRESS)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(createdBy = testUserId)
                )
            } returns requests

            mockMvc.perform(get("/api/v1/service-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Request 1"))
                .andExpect(jsonPath("$[1].title").value("Request 2"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should return empty list when user has no requests`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(createdBy = testUserId)
                )
            } returns emptyList()

            mockMvc.perform(get("/api/v1/service-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should filter by status`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("Pending", "Desc", testUserId, ServiceRequestStatus.PENDING)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(createdBy = testUserId, status = ServiceRequestStatus.PENDING)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests")
                    .param("status", ServiceRequestStatus.PENDING.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should filter by categoryGroup`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest(
                    "Facilities", "Desc", testUserId, ServiceRequestStatus.PENDING,
                    categoryGroup = ServiceCategoryGroup.FACILITIES, subCategory = ServiceSubCategory.ELEVATOR
                )
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(createdBy = testUserId, categoryGroup = ServiceCategoryGroup.FACILITIES)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests")
                    .param("categoryGroup", ServiceCategoryGroup.FACILITIES.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryGroup").value("FACILITIES"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should combine multiple filters`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest(
                    "Combined", "Desc", testUserId, ServiceRequestStatus.PENDING,
                    categoryGroup = ServiceCategoryGroup.FACILITIES, subCategory = ServiceSubCategory.ELEVATOR
                )
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(
                        createdBy = testUserId,
                        status = ServiceRequestStatus.PENDING,
                        categoryGroup = ServiceCategoryGroup.FACILITIES,
                        subCategory = ServiceSubCategory.ELEVATOR
                    )
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests")
                    .param("status", ServiceRequestStatus.PENDING.name)
                    .param("categoryGroup", ServiceCategoryGroup.FACILITIES.name)
                    .param("subCategory", ServiceSubCategory.ELEVATOR.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Combined"))
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== ADMIN ====================

    @Test
    fun `admin endpoint should return all requests when no filters provided`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("A", "Desc", testUserId, ServiceRequestStatus.PENDING),
                createMockServiceRequest("B", "Desc", testUserId, ServiceRequestStatus.IN_PROGRESS)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters()
                )
            } returns requests

            mockMvc.perform(get("/api/v1/service-requests/admin"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should filter by assignedTo`() {
        try {
            setupSecurityContext()

            val workerId = UserId.generate()
            val requests = listOf(
                createMockServiceRequest("Assigned", "Desc", testUserId, ServiceRequestStatus.ASSIGNED, assignedTo = workerId)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(assignedTo = workerId)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("assignedTo", workerId.value.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assignedTo").value(workerId.value.toString()))
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== ASSIGNED TO ME ====================

    @Test
    fun `getAssignedToMe should return requests assigned to current user`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("My Task", "Desc", UserId.generate(), ServiceRequestStatus.ASSIGNED, assignedTo = testUserId)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(assignedTo = testUserId)
                )
            } returns requests

            mockMvc.perform(get("/api/v1/service-requests/assigned-to-me"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My Task"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getAssignedToMe should filter by status`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("In Progress", "Desc", UserId.generate(), ServiceRequestStatus.IN_PROGRESS, assignedTo = testUserId)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(assignedTo = testUserId, status = ServiceRequestStatus.IN_PROGRESS)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/assigned-to-me")
                    .param("status", ServiceRequestStatus.IN_PROGRESS.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getAssignedToMe should return empty list when nothing assigned`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getRequests(
                    ServiceRequestFilters(assignedTo = testUserId)
                )
            } returns emptyList()

            mockMvc.perform(get("/api/v1/service-requests/assigned-to-me"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== START PROGRESS ====================

    @Test
    fun `startProgress should return updated request with expectedCompletionAt`() {
        try {
            setupSecurityContext()

            val expectedAt = Instant.parse("2025-06-01T12:00:00Z")
            val request = StartProgressRequest(expectedCompletionAt = expectedAt)
            val updatedRequest = createMockServiceRequest(
                "Task", "Desc", UserId.generate(), ServiceRequestStatus.IN_PROGRESS,
                assignedTo = testUserId, expectedCompletionAt = expectedAt
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.startProgress(
                    StartProgressCommand(
                        serviceRequestId = updatedRequest.id,
                        userId = testUserId,
                        expectedCompletionAt = expectedAt
                    )
                )
            } returns updatedRequest

            mockMvc.perform(
                patch("/api/v1/service-requests/{id}/start-progress", updatedRequest.id.value.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.expectedCompletionAt").value(expectedAt.epochSecond.toDouble()))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `startProgress should work without expectedCompletionAt`() {
        try {
            setupSecurityContext()

            val request = StartProgressRequest()
            val updatedRequest = createMockServiceRequest(
                "Task", "Desc", UserId.generate(), ServiceRequestStatus.IN_PROGRESS,
                assignedTo = testUserId
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.startProgress(
                    StartProgressCommand(
                        serviceRequestId = updatedRequest.id,
                        userId = testUserId,
                        expectedCompletionAt = null
                    )
                )
            } returns updatedRequest

            mockMvc.perform(
                patch("/api/v1/service-requests/{id}/start-progress", updatedRequest.id.value.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== COMPLETE ====================

    @Test
    fun `completeRequest should return completed request with report and cost`() {
        try {
            setupSecurityContext()

            val completeReq = CompleteRequest(
                completionReport = "Fixed the plumbing",
                completionCost = 150.0
            )
            val completedRequest = createMockServiceRequest(
                "Task", "Desc", UserId.generate(), ServiceRequestStatus.COMPLETED,
                assignedTo = testUserId, resolvedAt = Instant.now(),
                completionReport = "Fixed the plumbing", completionCost = 150.0
            )

            val idSlot = slot<CompleteServiceRequestCommand>()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.completeRequest(capture(idSlot))
            } returns completedRequest

            mockMvc.perform(
                patch("/api/v1/service-requests/{id}/complete", completedRequest.id.value.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(completeReq))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionReport").value("Fixed the plumbing"))
                .andExpect(jsonPath("$.completionCost").value(150.0))

            assertEquals("Fixed the plumbing", idSlot.captured.completionReport)
            assertEquals(150.0, idSlot.captured.completionCost)
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `completeRequest should work without report and cost`() {
        try {
            setupSecurityContext()

            val completeReq = CompleteRequest()
            val completedRequest = createMockServiceRequest(
                "Task", "Desc", UserId.generate(), ServiceRequestStatus.COMPLETED,
                assignedTo = testUserId, resolvedAt = Instant.now()
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.completeRequest(any())
            } returns completedRequest

            mockMvc.perform(
                patch("/api/v1/service-requests/{id}/complete", completedRequest.id.value.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(completeReq))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== CATEGORIES ====================

    @Test
    fun `getCategories should return all category groups with their subCategories`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getCategories(null) } returns buildCategoryOptionsResult()

            mockMvc.perform(get("/api/v1/service-requests/categories"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].value").value(ServiceCategoryGroup.FACILITIES.name))
                .andExpect(jsonPath("$.categories[0].label").value(ServiceCategoryGroup.FACILITIES.persianName))
                .andExpect(jsonPath("$.categories[0].subCategories[0].value").value(ServiceSubCategory.ELECTRICAL.name))
                .andExpect(jsonPath("$.categories[0].subCategories[0].label").value(ServiceSubCategory.ELECTRICAL.persianName))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getCategories should filter subcategories for the selected category group`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getCategories(ServiceCategoryGroup.FACILITIES.name) } returns buildCategoryOptionsResult()

            mockMvc.perform(get("/api/v1/service-requests/categories").param("categoryGroup", ServiceCategoryGroup.FACILITIES.name))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].value").value(ServiceCategoryGroup.FACILITIES.name))
                .andExpect(jsonPath("$.categories[0].subCategories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].subCategories[0].value").value(ServiceSubCategory.ELECTRICAL.name))
                .andExpect(jsonPath("$.categories[0].subCategories[0].label").value(ServiceSubCategory.ELECTRICAL.persianName))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getCategories should return 400 when the category group is invalid`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getCategories("INVALID_GROUP") } throws DomainValidationException("Category group 'INVALID_GROUP' is invalid")

            mockMvc.perform(get("/api/v1/service-requests/categories").param("categoryGroup", "INVALID_GROUP"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Category group 'INVALID_GROUP' is invalid"))
        } finally {
            cleanupSecurityContext()
        }
    }

    // ==================== HELPERS ====================

    private fun buildCategoryOptionsResult(): CategoryOptionsResult {
        return CategoryOptionsResult(
            categories = listOf(
                CategoryGroupOptionResult(
                    value = ServiceCategoryGroup.FACILITIES.name,
                    label = ServiceCategoryGroup.FACILITIES.persianName,
                    subCategories = listOf(
                        SubCategoryOptionResult(
                            value = ServiceSubCategory.ELECTRICAL.name,
                            label = ServiceSubCategory.ELECTRICAL.persianName
                        ),
                        SubCategoryOptionResult(
                            value = ServiceSubCategory.ELEVATOR.name,
                            label = ServiceSubCategory.ELEVATOR.persianName
                        )
                    )
                )
            )
        )
    }

    private fun createMockServiceRequest(
        title: String,
        description: String,
        createdBy: UserId,
        status: ServiceRequestStatus,
        location: String? = "Location",
        assignedTo: UserId? = null,
        resolvedAt: Instant? = null,
        categoryGroup: ServiceCategoryGroup = ServiceCategoryGroup.GENERAL,
        subCategory: ServiceSubCategory = ServiceSubCategory.GENERAL,
        expectedCompletionAt: Instant? = null,
        completionReport: String? = null,
        completionCost: Double? = null
    ): ServiceRequest {
        val now = Instant.now()
        return ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = title,
            description = description,
            location = location,
            createdBy = createdBy,
            updatedBy = createdBy,
            createdAt = now.minusSeconds(3600),
            updatedAt = now,
            status = status,
            assignedTo = assignedTo,
            resolvedAt = resolvedAt,
            expectedCompletionAt = expectedCompletionAt,
            completionReport = completionReport,
            completionCost = completionCost,
            categoryGroup = categoryGroup,
            subCategory = subCategory
        )
    }
}
