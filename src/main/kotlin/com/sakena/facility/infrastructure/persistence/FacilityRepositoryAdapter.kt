package com.sakena.facility.infrastructure.persistence

import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Adapter implementing the domain [FacilityRepository] port on top of Spring
 * Data JPA. This is the only place that knows about [FacilityEntity] and
 * [FacilityJpaRepository].
 */
@Component
class FacilityRepositoryAdapter(
    private val jpaRepository: FacilityJpaRepository,
) : FacilityRepository {

    override fun save(facility: Facility): Facility {
        val saved = jpaRepository.save(FacilityEntityMapper.toEntity(facility))
        return FacilityEntityMapper.toDomain(saved)
    }

    override fun findById(id: FacilityId): Facility? =
        jpaRepository.findByIdOrNull(id.value)?.let(FacilityEntityMapper::toDomain)

    override fun findAll(): List<Facility> =
        jpaRepository.findAll().map(FacilityEntityMapper::toDomain)

    override fun existsById(id: FacilityId): Boolean =
        jpaRepository.existsById(id.value)

    override fun deleteById(id: FacilityId) =
        jpaRepository.deleteById(id.value)
}
