package com.sakena.servicerequest.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface JpaServiceRequestRepository : JpaRepository<ServiceRequestJpaEntity, UUID>, JpaSpecificationExecutor<ServiceRequestJpaEntity> {
    fun findAllByCreatedBy(createdBy: UUID): List<ServiceRequestJpaEntity>
}
