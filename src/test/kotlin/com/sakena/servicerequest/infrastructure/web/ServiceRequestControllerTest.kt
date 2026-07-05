package com.sakena.servicerequest.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.servicerequest.application.CreateServiceRequestCommand
import com.sakena.servicerequest.application.ServiceRequestService
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestStatus
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
import org.springframework.validation.BindException
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.HashMap

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
                location = "Building A, Floor 2"
            )

            val createdRequest = createMockServiceRequest(
                title = request.title,
                description = request.description,
                location = request.location,
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
                .andExpect(jsonPath("$.id").value(createdRequest.id.value.toString()))
                .andExpect(jsonPath("$.title").value(request.title))
                .andExpect(jsonPath("$.description").value(request.description))
                .andExpect(jsonPath("$.location").value(request.location))
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
                location = "Building A, Floor 2"
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
                location = "Building A, Floor 2"
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
    fun `createRequest should allow null location`() {
        try {
            setupSecurityContext()

            val request = CreateServiceRequestRequest(
                title = "Broken Elevator",
                description = "The elevator is not working",
                location = null
            )

            val createdRequest = createMockServiceRequest(
                title = request.title,
                description = request.description,
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
                status = ServiceRequestStatus.APPROVED,
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
                location = "Building A, Floor 2"
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

    private fun createMockServiceRequest(
        title: String,
        description: String,
        createdBy: UserId,
        status: ServiceRequestStatus,
        location: String? = "Location",
        assignedTo: UserId? = null,
        resolvedAt: Instant? = null
    ): ServiceRequest {
        return ServiceRequest.reconstitute(
            id = ServiceRequestId.generate(),
            title = title,
            description = description,
            location = location,
            createdBy = createdBy,
            createdAt = Instant.now().minusSeconds(3600),
            updatedAt = Instant.now(),
            status = status,
            assignedTo = assignedTo,
            resolvedAt = resolvedAt
        )
    }
}
