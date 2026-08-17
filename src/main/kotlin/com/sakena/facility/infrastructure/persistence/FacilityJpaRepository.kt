package com.sakena.facility.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FacilityJpaRepository : JpaRepository<FacilityEntity, UUID> {
    fun findByIdAndBuildingId(id: UUID, buildingId: UUID): FacilityEntity?

    fun findAllByBuildingIdOrderByName(buildingId: UUID): List<FacilityEntity>

    fun existsByIdAndBuildingId(id: UUID, buildingId: UUID): Boolean
}
