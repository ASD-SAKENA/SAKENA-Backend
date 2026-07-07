package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestFilters
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ServiceRequestService(
    private val serviceRequestRepository: ServiceRequestRepository,
    private val userRepository: UserRepository
) {

    fun create(command: CreateServiceRequestCommand, currentUserId: UserId): ServiceRequest {
        val user = userRepository.findById(currentUserId)
            ?: throw IllegalArgumentException("User not found with id: $currentUserId")

        val request = ServiceRequest.create(
            title = command.title,
            description = command.description,
            location = command.location,
            createdBy = currentUserId,
            categoryGroup = command.categoryGroup,
            subCategory = command.subCategory
        )
        return serviceRequestRepository.save(request)
    }

    /**
     * Fetch service requests matching the given filters.
     * Pass an empty [ServiceRequestFilters]() to get all requests.
     */
    fun getRequests(filters: ServiceRequestFilters): List<ServiceRequest> {
        return serviceRequestRepository.findAllByFilters(filters)
    }

    fun getRequestById(id: ServiceRequestId): ServiceRequest? {
        return serviceRequestRepository.findById(id)
    }

    fun approveRequest(command: ApproveServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        val approved = request.approve(command.userId)
        return serviceRequestRepository.save(approved)
    }

    fun assignRequest(command: AssignServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(ServiceRequestId.fromString(command.serviceRequestId))
            ?: throw EntityNotFoundException("Service request not found")

        val worker = userRepository.findById(command.workerId)
            ?: throw IllegalArgumentException("Worker not found with id: ${command.workerId}")

        val assigned = request.assignTo(worker.id, command.userId)
        return serviceRequestRepository.save(assigned)
    }

    fun startProgress(command: StartProgressCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        if (request.assignedTo != command.userId) {
            throw DomainValidationException("Only the assigned staff member can start progress on this request")
        }

        val inProgress = request.startProgress(command.expectedCompletionAt)
        return serviceRequestRepository.save(inProgress)
    }

    fun completeRequest(command: CompleteServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        if (request.assignedTo != command.userId) {
            throw DomainValidationException("Only the assigned staff member can complete this request")
        }

        val completed = request.complete(
            userId = command.userId,
            completionReport = command.completionReport,
            completionCost = command.completionCost
        )
        return serviceRequestRepository.save(completed)
    }

    fun getCategories(categoryGroupValue: String?): CategoryOptionsResult {
        val selectedGroup = categoryGroupValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { rawValue ->
                ServiceCategoryGroup.entries.firstOrNull { it.name.equals(rawValue, ignoreCase = true) }
                    ?: throw DomainValidationException("Category group '$rawValue' is invalid")
            }

        val groups = selectedGroup?.let { listOf(it) } ?: ServiceCategoryGroup.entries

        val categories = groups.map { group ->
            CategoryGroupOptionResult(
                value = group.name,
                label = group.persianName,
                subCategories = ServiceSubCategory.entries
                    .filter { it.group == group }
                    .map { subCategory ->
                        SubCategoryOptionResult(
                            value = subCategory.name,
                            label = subCategory.persianName
                        )
                    }
            )
        }

        return CategoryOptionsResult(categories = categories)
    }
}
