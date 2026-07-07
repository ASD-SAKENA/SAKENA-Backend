package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestFilters
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import jakarta.persistence.criteria.Predicate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

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

    override fun findAll(): List<ServiceRequest> {
        return jpa.findAll().map { toDomain(it) }
    }

    override fun findAllByFilters(filters: ServiceRequestFilters): List<ServiceRequest> {
        val spec = org.springframework.data.jpa.domain.Specification<ServiceRequestJpaEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            filters.createdBy?.let { predicates.add(cb.equal(root.get<UUID>("createdBy"), it.value)) }
            filters.updatedBy?.let { predicates.add(cb.equal(root.get<UUID>("updatedBy"), it.value)) }
            filters.assignedTo?.let { predicates.add(cb.equal(root.get<UUID>("assignedTo"), it.value)) }
            filters.status?.let { predicates.add(cb.equal(root.get<ServiceRequestStatus>("status"), it)) }
            filters.categoryGroup?.let { predicates.add(cb.equal(root.get<ServiceCategoryGroup>("categoryGroup"), it)) }
            filters.subCategory?.let { predicates.add(cb.equal(root.get<ServiceSubCategory>("subCategory"), it)) }
            filters.createdFrom?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), it)) }
            filters.createdTo?.let { predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), it)) }
            filters.updatedFrom?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), it)) }
            filters.updatedTo?.let { predicates.add(cb.lessThanOrEqualTo(root.get("updatedAt"), it)) }

            cb.and(*predicates.toTypedArray())
        }

        return jpa.findAll(spec).map { toDomain(it) }
    }

    private fun toJpaEntity(domain: ServiceRequest): ServiceRequestJpaEntity {
        return ServiceRequestJpaEntity(
            id = domain.id.value,
            title = domain.title,
            description = domain.description,
            location = domain.location,
            categoryGroup = domain.categoryGroup,
            subCategory = domain.subCategory,
            createdBy = domain.createdBy.value,
            createdAt = domain.createdAt,
            updatedBy = domain.updatedBy.value,
            updatedAt = domain.updatedAt,
            status = domain.status,
            assignedTo = domain.assignedTo?.value,
            resolvedAt = domain.resolvedAt,
            expectedCompletionAt = domain.expectedCompletionAt,
            completionReport = domain.completionReport,
            completionCost = domain.completionCost
        )
    }

    private fun toDomain(entity: ServiceRequestJpaEntity): ServiceRequest {
        return ServiceRequest.reconstitute(
            id = ServiceRequestId(entity.id),
            title = entity.title,
            description = entity.description,
            location = entity.location,
            categoryGroup = entity.categoryGroup,
            subCategory = entity.subCategory,
            createdBy = UserId(entity.createdBy),
            updatedBy = UserId(entity.updatedBy),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            status = entity.status,
            assignedTo = entity.assignedTo?.let { UserId(it) },
            resolvedAt = entity.resolvedAt,
            expectedCompletionAt = entity.expectedCompletionAt,
            completionReport = entity.completionReport,
            completionCost = entity.completionCost
        )
    }
}
