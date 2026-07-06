package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainValidationException
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
        // Ensure the user exists
        val user = userRepository.findById(currentUserId)
            ?: throw IllegalArgumentException("User not found with id: $currentUserId")

        // Optionally check role: only residents can create? We'll enforce in controller or here.
        // For now, we allow any authenticated user.

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

    fun getMyRequests(userId: UserId): List<ServiceRequest> {
        return serviceRequestRepository.findAllByCreatedBy(userId)
    }

    fun getRequestById(id: ServiceRequestId): ServiceRequest? {
        return serviceRequestRepository.findById(id)
    }
}
