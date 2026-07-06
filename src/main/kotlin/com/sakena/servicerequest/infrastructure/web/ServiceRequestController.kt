package com.sakena.servicerequest.infrastructure.web

import com.sakena.servicerequest.application.CreateServiceRequestCommand
import com.sakena.servicerequest.application.ServiceRequestService
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/service-requests")
@Tag(name = "Service Requests", description = "Manage building service requests")
@SecurityRequirement(name = "bearerAuth")  // ensures Swagger includes bearer token
class ServiceRequestController(
    private val profileService: ProfileService,
    private val serviceRequestService: ServiceRequestService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")   // <-- این خط را اضافه کنید
    @Operation(summary = "Create a new service request (resident)")
    fun createRequest(@RequestBody @Valid request: CreateServiceRequestRequest): ServiceRequestResponse {
        request.validateOrThrow()

        val userId = getCurrentUserId()
        val command = CreateServiceRequestCommand(
            title = request.title!!.trim(),
            description = request.description!!.trim(),
            location = request.location?.takeIf { it.isNotBlank() }?.trim(),
            categoryGroup = request.categoryGroup!!,
            subCategory = request.subCategory!!
        )
        val created = serviceRequestService.create(command, userId)
        return ServiceRequestResponse.fromDomain(created)
    }

    @GetMapping
    @Operation(summary = "Get all service requests for the current user")
    @SecurityRequirement(name = "bearerAuth")   // <-- این خط را اضافه کنید
    fun getMyRequests(): List<ServiceRequestResponse> {
        val userId = getCurrentUserId()
        val requests = serviceRequestService.getMyRequests(userId)
        return requests.map { ServiceRequestResponse.fromDomain(it) }
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id

    }
}
