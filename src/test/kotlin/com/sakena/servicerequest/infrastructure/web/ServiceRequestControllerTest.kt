package com.sakena.servicerequest.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.servicerequest.application.GetAllServiceRequestsQuery
import com.sakena.servicerequest.application.AssignServiceRequestCommand
import com.sakena.servicerequest.application.ApproveServiceRequestCommand
import com.sakena.servicerequest.application.CategoryGroupOptionResult
import com.sakena.servicerequest.application.CategoryOptionsResult
import com.sakena.servicerequest.application.CreateServiceRequestCommand
import com.sakena.servicerequest.application.ServiceRequestService
import com.sakena.servicerequest.application.SubCategoryOptionResult
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private val objectMapper = ObjectMapper()

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
    fun `createRequest should return 500 when category value is invalid`() {
        try {
            setupSecurityContext()

            val invalidJson = """
                {
                  "title": "Broken Elevator",
                  "description": "The elevator is not working",
                  "location": "Building A",
                  "categoryGroup": "INVALID_GROUP",
                  "subCategory": "GENERAL"
                }
            """.trimIndent()

            mockMvc.perform(
                post("/api/v1/service-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson)
            )
                .andExpect(status().isInternalServerError)
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

    @Test
    fun `createRequest should allow null location`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "The elevator is not working",
                location = null,
                categoryGroup = ServiceCategoryGroup.GENERAL,
                subCategory = ServiceSubCategory.GENERAL
            )

            val createdRequest = createMockServiceRequest(
                title = request.title!!,
                description = request.description!!,
                location = null,
                createdBy = testUserId,
                status = ServiceRequestStatus.PENDING
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
                .andExpect(jsonPath("$.location").doesNotExist())
        } finally {
            cleanupSecurityContext()
        }
    }

    // --- Admin endpoint tests ---

    @Test
    fun `admin endpoint should return all requests when no filters provided`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("Request 1", "Desc 1", testUserId, ServiceRequestStatus.PENDING),
                createMockServiceRequest("Request 2", "Desc 2", testUserId, ServiceRequestStatus.IN_PROGRESS)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(GetAllServiceRequestsQuery())
            } returns requests

            mockMvc.perform(get("/api/v1/service-requests/admin"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Request 1"))
                .andExpect(jsonPath("$[1].title").value("Request 2"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should filter by status`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("Pending Request", "Desc", testUserId, ServiceRequestStatus.PENDING)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(status = ServiceRequestStatus.PENDING)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("status", ServiceRequestStatus.PENDING.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Pending Request"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should filter by categoryGroup`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest(
                    title = "Facilities Request",
                    description = "Desc",
                    createdBy = testUserId,
                    status = ServiceRequestStatus.PENDING,
                    categoryGroup = ServiceCategoryGroup.FACILITIES,
                    subCategory = ServiceSubCategory.ELEVATOR
                )
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(categoryGroup = ServiceCategoryGroup.FACILITIES)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
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
    fun `admin endpoint should filter by subCategory`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest(
                    title = "Elevator Request",
                    description = "Desc",
                    createdBy = testUserId,
                    status = ServiceRequestStatus.PENDING,
                    categoryGroup = ServiceCategoryGroup.FACILITIES,
                    subCategory = ServiceSubCategory.ELEVATOR
                )
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(subCategory = ServiceSubCategory.ELEVATOR)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("subCategory", ServiceSubCategory.ELEVATOR.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subCategory").value("ELEVATOR"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should filter by createdFrom and createdTo`() {
        try {
            setupSecurityContext()

            val from = Instant.parse("2024-01-01T00:00:00Z")
            val to = Instant.parse("2024-12-31T23:59:59Z")
            val requests = listOf(
                createMockServiceRequest("Date Filtered", "Desc", testUserId, ServiceRequestStatus.PENDING)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(createdFrom = from, createdTo = to)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("createdFrom", from.toString())
                    .param("createdTo", to.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Date Filtered"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should filter by updatedFrom and updatedTo`() {
        try {
            setupSecurityContext()

            val from = Instant.parse("2024-01-01T00:00:00Z")
            val to = Instant.parse("2024-12-31T23:59:59Z")
            val requests = listOf(
                createMockServiceRequest("Updated Date Filter", "Desc", testUserId, ServiceRequestStatus.PENDING)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(updatedFrom = from, updatedTo = to)
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("updatedFrom", from.toString())
                    .param("updatedTo", to.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Updated Date Filter"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should combine multiple filters`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest(
                    title = "Combined Filter",
                    description = "Desc",
                    createdBy = testUserId,
                    status = ServiceRequestStatus.PENDING,
                    categoryGroup = ServiceCategoryGroup.FACILITIES,
                    subCategory = ServiceSubCategory.ELEVATOR
                )
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(
                        status = ServiceRequestStatus.PENDING,
                        categoryGroup = ServiceCategoryGroup.FACILITIES,
                        subCategory = ServiceSubCategory.ELEVATOR
                    )
                )
            } returns requests

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("status", ServiceRequestStatus.PENDING.name)
                    .param("categoryGroup", ServiceCategoryGroup.FACILITIES.name)
                    .param("subCategory", ServiceSubCategory.ELEVATOR.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Combined Filter"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `admin endpoint should return empty list when no requests match`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every {
                serviceRequestService.getAllRequests(
                    GetAllServiceRequestsQuery(status = ServiceRequestStatus.APPROVED)
                )
            } returns emptyList()

            mockMvc.perform(
                get("/api/v1/service-requests/admin")
                    .param("status", ServiceRequestStatus.APPROVED.name)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        } finally {
            cleanupSecurityContext()
        }
    }

    // --- My Requests tests ---

    @Test
    fun `getMyRequests should return list of user's requests`() {
        try {
            setupSecurityContext()

            val requests = listOf(
                createMockServiceRequest("Request 1", "Desc 1", testUserId, ServiceRequestStatus.PENDING),
                createMockServiceRequest("Request 2", "Desc 2", testUserId, ServiceRequestStatus.IN_PROGRESS),
                createMockServiceRequest("Request 3", "Desc 3", testUserId, ServiceRequestStatus.COMPLETED)
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getMyRequests(testUserId) } returns requests

            mockMvc.perform(get("/api/v1/service-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Request 1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].title").value("Request 2"))
                .andExpect(jsonPath("$[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[2].title").value("Request 3"))
                .andExpect(jsonPath("$[2].status").value("COMPLETED"))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should return empty list when user has no requests`() {
        try {
            setupSecurityContext()

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getMyRequests(testUserId) } returns emptyList()

            mockMvc.perform(get("/api/v1/service-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should handle requests with assignedTo`() {
        try {
            setupSecurityContext()

            val workerId = UserId.generate()
            val request = createMockServiceRequest(
                title = "Assigned Request",
                description = "Desc",
                createdBy = testUserId,
                status = ServiceRequestStatus.ASSIGNED,
                assignedTo = workerId
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getMyRequests(testUserId) } returns listOf(request)

            mockMvc.perform(get("/api/v1/service-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].assignedTo").value(workerId.value.toString()))
        } finally {
            cleanupSecurityContext()
        }
    }

    @Test
    fun `getMyRequests should handle completed requests with resolvedAt`() {
        try {
            setupSecurityContext()

            val resolvedAt = Instant.now()
            val request = createMockServiceRequest(
                title = "Completed Request",
                description = "Desc",
                createdBy = testUserId,
                status = ServiceRequestStatus.COMPLETED,
                resolvedAt = resolvedAt
            )

            every { profileService.getUserByUsername(testUsername) } returns testUser
            every { serviceRequestService.getMyRequests(testUserId) } returns listOf(request)

            mockMvc.perform(get("/api/v1/service-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].resolvedAt").exists())
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
        subCategory: ServiceSubCategory = ServiceSubCategory.GENERAL
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
            categoryGroup = categoryGroup,
            subCategory = subCategory
        )
    }
}
