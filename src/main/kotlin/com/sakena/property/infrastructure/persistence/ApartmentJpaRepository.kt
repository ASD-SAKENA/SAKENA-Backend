package com.sakena.property.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApartmentJpaRepository : JpaRepository<ApartmentEntity, UUID> {
    fun findAllByBuildingId(buildingId: UUID): List<ApartmentEntity>

    fun existsByBuildingId(buildingId: UUID): Boolean
}
