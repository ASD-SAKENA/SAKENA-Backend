package com.sakena.servicerequest.application

import com.sakena.property.domain.ApartmentRepository
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
import com.sakena.user.domain.User
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
) {

    /**
     * A request tied to a resident's apartment belongs to that apartment's
     * building, and only its manager may act on it. A request with no
     * apartment (filed by staff, or a resident with no unit assigned) has no
     * building to scope to, so every manager can still act on those — same
     * as before this check existed.
     */
    private fun requireBuildingOwnership(request: ServiceRequest, manager: User) {
        val apartmentId = request.requestingApartmentId ?: return
        val apartment = apartmentRepository.findById(apartmentId) ?: return
        if (manager.managedBuildingId != apartment.buildingId) {
            throw DomainForbiddenException("You do not manage the building this request belongs to")
        }
    }

    private fun requireManagerCanAct(request: ServiceRequest, managerId: UserId) {
        val manager = userRepository.findById(managerId)
            ?: throw EntityNotFoundException("User with id '$managerId' was not found")
        if (manager.role != Role.MANAGER) {
            throw DomainForbiddenException("Only a manager can perform this action")
        }
        requireBuildingOwnership(request, manager)
    }

    private fun buildingOf(request: ServiceRequest): BuildingId? =
        request.requestingApartmentId?.let { apartmentRepository.findById(it)?.buildingId }

    fun create(command: CreateServiceRequestCommand, currentUserId: UserId): ServiceRequest {
        userRepository.findById(currentUserId)
            ?: throw IllegalArgumentException("User not found with id: $currentUserId")
        val requestingApartmentId = residencyRepository.findActiveByResident(currentUserId)?.apartmentId

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

    /**
     * The manager admin queue, scoped to the requester's own building: a
     * request tied to an apartment is only visible to that apartment's
     * building's manager; a request with no apartment (staff-filed, or an
     * unassigned resident) stays visible to every manager, as it always was.
     */
    fun getRequestsForManager(filters: ServiceRequestFilters, managerId: UserId): List<ServiceRequest> {
        val manager = userRepository.findById(managerId)
            ?: throw EntityNotFoundException("User with id '$managerId' was not found")
        return serviceRequestRepository.findAllByFilters(filters).filter { request ->
            val buildingId = buildingOf(request) ?: return@filter true
            buildingId == manager.managedBuildingId
        }
    }

    fun getRequestById(id: ServiceRequestId): ServiceRequest? {
        return serviceRequestRepository.findById(id)
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
        requireManagerCanAct(request, command.userId)

        val approved = request.approve(command.userId)
        return serviceRequestRepository.save(approved)
    }

    fun rejectRequest(command: RejectServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")
        requireManagerCanAct(request, command.userId)

        val rejected = request.reject(command.userId)
        return serviceRequestRepository.save(rejected)
    }

    fun assignRequest(command: AssignServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(ServiceRequestId.fromString(command.serviceRequestId))
            ?: throw EntityNotFoundException("Service request not found")
        requireManagerCanAct(request, command.userId)

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

    fun assignCostResponsibility(command: AssignServiceCostResponsibilityCommand): ServiceRequest {
        val manager = userRepository.findById(command.managerId)
            ?: throw EntityNotFoundException("User with id '${command.managerId}' was not found")
        if (manager.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can assign service request cost responsibility")
        }

        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")
        requireBuildingOwnership(request, manager)

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
}
