package com.sakena.servicerequest.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.BuildingId
import com.sakena.rating.application.RatingService
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
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.sakena.servicerequest.domain.ServiceRequestEvent
import com.sakena.servicerequest.domain.ServiceRequestEventRepository
import com.sakena.servicerequest.domain.ServiceRequestEventType

@Service
@Transactional
class ServiceRequestService(
    private val serviceRequestRepository: ServiceRequestRepository,
    private val userRepository: UserRepository,
    private val residencyRepository: ResidencyRepository,
    private val apartmentRepository: ApartmentRepository,
    private val ratingService: RatingService,
    private val eventRepository: ServiceRequestEventRepository,
    private val objectMapper: ObjectMapper,
) {

    private fun recordEvent(request: ServiceRequest, type: ServiceRequestEventType, performedBy: UserId? = null, payload: String? = null) {
        // Build a JSON payload that always contains the full request plus
        // the optional short "note" previously used in several calls.
        val payloadObj = mapOf(
            "note" to payload,
            "request" to serializeRequest(request)
        )
        val jsonPayload = try {
            objectMapper.writeValueAsString(payloadObj)
        } catch (e: Exception) {
            // Fallback to the previous simple payload string when serialization fails
            payload ?: ""
        }

        val event = ServiceRequestEvent(
            serviceRequestId = request.id,
            type = type,
            performedBy = performedBy,
            payload = jsonPayload,
        )
        eventRepository.save(event)
    }

    private fun serializeRequest(request: ServiceRequest): Map<String, Any?> {
        return mapOf(
            "id" to request.id.value.toString(),
            "title" to request.title,
            "description" to request.description,
            "location" to request.location,
            "categoryGroup" to request.categoryGroup.name,
            "subCategory" to request.subCategory.name,
            "createdBy" to request.createdBy.value.toString(),
            "updatedBy" to request.updatedBy.value.toString(),
            "createdAt" to request.createdAt.toString(),
            "updatedAt" to request.updatedAt.toString(),
            "status" to request.status.name,
            "assignedTo" to request.assignedTo?.value?.toString(),
            "resolvedAt" to request.resolvedAt?.toString(),
            "expectedCompletionAt" to request.expectedCompletionAt?.toString(),
            "completionReport" to request.completionReport,
            "completionCost" to request.completionCost,
            "costResponsibility" to request.costResponsibility?.name,
            "requestingApartmentId" to request.requestingApartmentId?.value?.toString(),
        )
    }

    private fun saveAndRecordEvent(
        request: ServiceRequest,
        type: ServiceRequestEventType,
        performedBy: UserId?,
        noteOf: (ServiceRequest) -> String? = { null },
    ): ServiceRequest {
        val saved = serviceRequestRepository.save(request)
        recordEvent(saved, type, performedBy, noteOf(saved))
        return saved
    }


    /**
     * A request tied to a resident's apartment belongs to that apartment's
     * building, and only its manager may act on it. A request with no
     * apartment (filed by staff before this check existed) has no building to
     * scope to, so every manager can still act on those.
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
            ?: throw DomainForbiddenException("You must be an active resident of a unit to file a request")

        val request = ServiceRequest.create(
            title = command.title,
            description = command.description,
            location = command.location,
            createdBy = currentUserId,
            categoryGroup = command.categoryGroup,
            subCategory = command.subCategory,
            requestingApartmentId = requestingApartmentId,
        )
        return saveAndRecordEvent(
            request = request,
            type = ServiceRequestEventType.CREATED,
            performedBy = currentUserId,
            noteOf = { "title=${it.title}" },
        )
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
        return saveAndRecordEvent(
            request = updated,
            type = ServiceRequestEventType.UPDATED,
            performedBy = command.userId,
            noteOf = { "title=${it.title}" },
        )
    }

    fun approveRequest(command: ApproveServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")
        requireManagerCanAct(request, command.userId)

        val approved = request.approve(command.userId)
        return saveAndRecordEvent(
            request = approved,
            type = ServiceRequestEventType.APPROVED,
            performedBy = command.userId,
            noteOf = { "status=${it.status}" },
        )
    }

    fun rejectRequest(command: RejectServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")
        requireManagerCanAct(request, command.userId)

        val rejected = request.reject(command.userId)
        return saveAndRecordEvent(
            request = rejected,
            type = ServiceRequestEventType.REJECTED,
            performedBy = command.userId,
            noteOf = { "status=${it.status}" },
        )
    }

    fun assignRequest(command: AssignServiceRequestCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(ServiceRequestId.fromString(command.serviceRequestId))
            ?: throw EntityNotFoundException("Service request not found")
        requireManagerCanAct(request, command.userId)

        val worker = userRepository.findById(command.workerId)
            ?: throw EntityNotFoundException("Worker not found with id: ${command.workerId}")
        // Staff are a shared pool serving several buildings, so the only rule
        // here is that the target is actually a service-staff account.
        if (worker.role != Role.STAFF) {
            throw DomainValidationException("Only a service staff account can be assigned a request")
        }

        val assigned = request.assignTo(worker.id, command.userId)
        return saveAndRecordEvent(
            request = assigned,
            type = ServiceRequestEventType.ASSIGNED,
            performedBy = command.userId,
            noteOf = { "worker=${worker.id}" },
        )
    }

    fun startProgress(command: StartProgressCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        if (request.assignedTo != command.userId) {
            throw DomainValidationException("Only the assigned staff member can start progress on this request")
        }

        val inProgress = request.startProgress(command.expectedCompletionAt)
        return saveAndRecordEvent(
            request = inProgress,
            type = ServiceRequestEventType.STARTED_PROGRESS,
            performedBy = command.userId,
            noteOf = { "expectedCompletionAt=${it.expectedCompletionAt}" },
        )
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
        return saveAndRecordEvent(
            request = completed,
            type = ServiceRequestEventType.COMPLETED,
            performedBy = command.userId,
            noteOf = { "cost=${it.completionCost}" },
        )
    }

    fun confirmCompletionAndRate(command: ConfirmCompletionCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        val confirmed = request.confirmCompletion(command.userId)
        val staffId = confirmed.assignedTo
            ?: throw DomainValidationException("Service request has no assigned staff member to rate")
        val saved = saveAndRecordEvent(
            request = confirmed,
            type = ServiceRequestEventType.CONFIRMED,
            performedBy = command.userId,
            noteOf = { "confirmedBy=${command.userId}" },
        )
        ratingService.rate(saved.id, staffId, command.userId, command.score)
        return saved
    }

    fun rejectCompletion(command: RejectCompletionCommand): ServiceRequest {
        val request = serviceRequestRepository.findById(command.serviceRequestId)
            ?: throw EntityNotFoundException("Service request not found")

        val rejected = request.rejectCompletion(command.userId)
        return saveAndRecordEvent(
            request = rejected,
            type = ServiceRequestEventType.REJECTED_COMPLETION,
            performedBy = command.userId,
        )
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
        return saveAndRecordEvent(
            request = updated,
            type = ServiceRequestEventType.COST_RESPONSIBILITY_ASSIGNED,
            performedBy = command.managerId,
            noteOf = { "responsibility=${it.costResponsibility}" },
        )
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
