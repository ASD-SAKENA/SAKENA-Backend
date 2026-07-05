package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Repository
import kotlin.collections.map

@Repository
class ServiceRequestRepositoryImpl(
    private val jpa: JpaServiceRequestRepository
) : ServiceRequestRepository {

    override fun save(request: ServiceRequest): ServiceRequest {
        val entity = toJpaEntity(request)
        val saved = jpa.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: ServiceRequestId): ServiceRequest? {
        return jpa.findById(id.value).orElse(null)?.let { toDomain(it) }
    }

    override fun findAllByCreatedBy(userId: UserId): List<ServiceRequest> {
        return jpa.findAllByCreatedBy(userId.value).map { toDomain(it) }
    }

    override fun findAll(): List<ServiceRequest> {
        return jpa.findAll().map { toDomain(it) }
    }

    private fun toJpaEntity(domain: ServiceRequest): ServiceRequestJpaEntity {
        return ServiceRequestJpaEntity(
            id = domain.id.value,
            title = domain.title,
            description = domain.description,
            location = domain.location,
            createdBy = domain.createdBy.value,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            status = domain.status,
            assignedTo = domain.assignedTo?.value,
            resolvedAt = domain.resolvedAt
        )
    }

    private fun toDomain(entity: ServiceRequestJpaEntity): ServiceRequest {
        return ServiceRequest.reconstitute(
            id = ServiceRequestId(entity.id),
            title = entity.title,
            description = entity.description,
            location = entity.location,
            createdBy = UserId(entity.createdBy),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            status = entity.status,
            assignedTo = entity.assignedTo?.let { UserId(it) },
            resolvedAt = entity.resolvedAt
        )
    }
}
