package com.sakena.property.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuildingJpaRepository : JpaRepository<BuildingEntity, UUID> {
    fun findByManagerId(managerId: UUID): BuildingEntity?

    fun existsByIdAndManagerId(id: UUID, managerId: UUID): Boolean
}
