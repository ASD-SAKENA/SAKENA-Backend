package com.sakena.servicerequest.infrastructure.web

import com.sakena.servicerequest.application.AssignServiceRequestCommand
import com.sakena.servicerequest.application.ApproveServiceRequestCommand
import com.sakena.servicerequest.application.CreateServiceRequestCommand
import com.sakena.servicerequest.application.GetAllServiceRequestsQuery
import com.sakena.servicerequest.application.ServiceRequestService
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.Instant

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
    @SecurityRequirement(name = "bearerAuth")
    fun getMyRequests(): List<ServiceRequestResponse> {
        val userId = getCurrentUserId()
        val requests = serviceRequestService.getMyRequests(userId)
        return requests.map { ServiceRequestResponse.fromDomain(it) }
    }

    @GetMapping("/admin")
    @Operation(summary = "Get all service requests with filtering (admin/manager only)")
    @SecurityRequirement(name = "bearerAuth")
    fun getAllRequests(
        @RequestParam(required = false) status: ServiceRequestStatus?,
        @RequestParam(required = false) categoryGroup: ServiceCategoryGroup?,
        @RequestParam(required = false) subCategory: ServiceSubCategory?,
        @RequestParam(required = false) createdFrom: Instant?,
        @RequestParam(required = false) createdTo: Instant?,
        @RequestParam(required = false) updatedFrom: Instant?,
        @RequestParam(required = false) updatedTo: Instant?
    ): List<ServiceRequestResponse> {
        val query = GetAllServiceRequestsQuery(
            status = status,
            categoryGroup = categoryGroup,
            subCategory = subCategory,
            createdFrom = createdFrom,
            createdTo = createdTo,
            updatedFrom = updatedFrom,
            updatedTo = updatedTo
        )
        val requests = serviceRequestService.getAllRequests(query)
        return requests.map { ServiceRequestResponse.fromDomain(it) }
    }

    @GetMapping("/categories")
    @Operation(summary = "Get service request categories and subcategories for the frontend")
    fun getCategories(
        @RequestParam(required = false, name = "categoryGroup") categoryGroupValue: String?,
        @RequestParam(required = false, name = "category") categoryValue: String?
    ): CategoryOptionsResponse {
        val result = serviceRequestService.getCategories(categoryGroupValue ?: categoryValue)

        return CategoryOptionsResponse(
            categories = result.categories.map { group ->
                CategoryGroupResponse(
                    value = group.value,
                    label = group.label,
                    subCategories = group.subCategories.map { subCategory ->
                        CategoryOptionResponse(
                            value = subCategory.value,
                            label = subCategory.label
                        )
                    }
                )
            }
        )
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve a pending service request")
    fun approveRequest(@PathVariable id: String): ServiceRequestResponse {
        val command = ApproveServiceRequestCommand(serviceRequestId = ServiceRequestId.fromString(id), userId = getCurrentUserId())
        val approved = serviceRequestService.approveRequest(command)
        return ServiceRequestResponse.fromDomain(approved)
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign an approved service request to a worker")
    fun assignRequest(@PathVariable id: String, @RequestParam workerId: String): ServiceRequestResponse {
        val command = AssignServiceRequestCommand(
            serviceRequestId = id,
            workerId = com.sakena.user.domain.UserId.fromString(workerId),
            userId = getCurrentUserId()
        )
        val assigned = serviceRequestService.assignRequest(command)
        return ServiceRequestResponse.fromDomain(assigned)
    }



    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id

    }
}
