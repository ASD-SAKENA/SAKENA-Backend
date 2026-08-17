package com.sakena.facility.infrastructure.persistence

import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.model.BuildingId
import org.springframework.stereotype.Component

@Component
class FacilityRepositoryAdapter(
    private val jpaRepository: FacilityJpaRepository,
) : FacilityRepository {

    override fun save(facility: Facility): Facility {
        val saved = jpaRepository.save(FacilityEntityMapper.toEntity(facility))
        return FacilityEntityMapper.toDomain(saved)
    }

    override fun findByIdAndBuildingId(id: FacilityId, buildingId: BuildingId): Facility? =
        jpaRepository.findByIdAndBuildingId(id.value, buildingId.value)?.let(FacilityEntityMapper::toDomain)

    override fun findAllByBuildingId(buildingId: BuildingId): List<Facility> =
        jpaRepository.findAllByBuildingIdOrderByName(buildingId.value).map(FacilityEntityMapper::toDomain)

    override fun existsByIdAndBuildingId(id: FacilityId, buildingId: BuildingId): Boolean =
        jpaRepository.existsByIdAndBuildingId(id.value, buildingId.value)

    override fun deleteById(id: FacilityId) =
        jpaRepository.deleteById(id.value)
}
