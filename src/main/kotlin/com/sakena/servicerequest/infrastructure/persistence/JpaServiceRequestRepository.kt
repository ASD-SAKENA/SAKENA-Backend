package com.sakena.servicerequest.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaServiceRequestRepository : JpaRepository<ServiceRequestJpaEntity, UUID> {
    fun findAllByCreatedBy(createdBy: UUID): List<ServiceRequestJpaEntity>
}
