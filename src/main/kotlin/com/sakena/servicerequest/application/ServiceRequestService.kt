package com.sakena.servicerequest.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestFilters
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ServiceRequestService(
    private val serviceRequestRepository: ServiceRequestRepository,
    private val userRepository: UserRepository,
    private val residencyRepository: ResidencyRepository,
    private val apartmentRepository: ApartmentRepository,
    private val buildingAccess: BuildingAccess,
) {

    fun create(command: CreateServiceRequestCommand, currentUserId: UserId): ServiceRequest {
        userRepository.findById(currentUserId)
            ?: throw IllegalArgumentException("User not found with id: $currentUserId")
        val requestingApartmentId = residencyRepository.findActiveByResident(currentUserId)?.apartmentId
            ?: throw DomainForbiddenException("You must belong to a building to create a service request")

        val request = ServiceRequest.create(
            title = command.title,
            description = command.description,
            location = command.location,
            createdBy = currentUserId,
            categoryGroup = command.categoryGroup,
            subCategory = command.subCategory,
            requestingApartmentId = requestingApartmentId,
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

    @Transactional(readOnly = true)
    fun getManagerRequests(
        filters: ServiceRequestFilters,
        managerId: UserId,
    ): List<ServiceRequest> {
        val buildingId = buildingAccess.managedBuildingId(managerId)
        val apartmentIds = apartmentRepository.findAllByBuildingId(buildingId).map { it.id }.toSet()
        return serviceRequestRepository.findAllByApartmentIdsAndFilters(apartmentIds, filters)
    }

    fun updateRequest(command: UpdateServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        if (request.createdBy != command.userId) {
            throw DomainForbiddenException("Only the resident who created this request can edit it")
        }

        val updated = request.updateDetails(
            title = command.title,
            description = command.description,
            location = command.location,
            categoryGroup = command.categoryGroup,
            subCategory = command.subCategory,
            userId = command.userId,
        )
        return serviceRequestRepository.save(updated)
    }

    fun approveRequest(command: ApproveServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        requireManagerAccess(request, command.userId)

        val approved = request.approve(command.userId)
        return serviceRequestRepository.save(approved)
    }

    fun rejectRequest(command: RejectServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        requireManagerAccess(request, command.userId)

        val rejected = request.reject(command.userId)
        return serviceRequestRepository.save(rejected)
    }

    fun assignRequest(command: AssignServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(ServiceRequestId.fromString(command.serviceRequestId))
            ?: throw EntityNotFoundException("Service request not found")

        val buildingId = buildingIdOf(request)
        buildingAccess.requireManagerAccess(buildingId, command.userId)

        val worker = userRepository.findById(command.workerId)
            ?: throw IllegalArgumentException("Worker not found with id: ${command.workerId}")
        if (worker.role != Role.STAFF) {
            throw DomainForbiddenException("Service requests can only be assigned to staff")
        }
        buildingAccess.requireStaffAccess(buildingId, worker.id)

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

    fun assignCostResponsibility(command: AssignServiceCostResponsibilityCommand): ServiceRequest {
        val manager = userRepository.findById(command.managerId)
            ?: throw EntityNotFoundException("User with id '${command.managerId}' was not found")
        if (manager.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can assign service request cost responsibility")
        }

        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        requireManagerAccess(request, command.managerId)

        val updated = request.assignCostResponsibility(
            responsibility = command.responsibility,
            userId = command.managerId,
        )
        return serviceRequestRepository.save(updated)
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

    private fun requireManagerAccess(request: ServiceRequest, managerId: UserId) {
        buildingAccess.requireManagerAccess(buildingIdOf(request), managerId)
    }

    private fun buildingIdOf(request: ServiceRequest): BuildingId {
        val apartmentId = request.requestingApartmentId
            ?: throw DomainForbiddenException("This legacy request is not assigned to a building")
        return apartmentRepository.findById(apartmentId)?.buildingId
            ?: throw EntityNotFoundException("Requesting apartment with id '$apartmentId' was not found")
    }
}
