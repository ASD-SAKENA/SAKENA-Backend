package com.sakena.property.infrastructure.persistence

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ApartmentRepositoryAdapter(
    private val jpaRepository: ApartmentJpaRepository,
) : ApartmentRepository {

    override fun save(apartment: Apartment): Apartment {
        val saved = jpaRepository.save(ApartmentEntityMapper.toEntity(apartment))
        return ApartmentEntityMapper.toDomain(saved)
    }

    override fun findById(id: ApartmentId): Apartment? =
        jpaRepository.findByIdOrNull(id.value)?.let(ApartmentEntityMapper::toDomain)

    override fun findAll(): List<Apartment> =
        jpaRepository.findAll().map(ApartmentEntityMapper::toDomain)

    override fun findAllByBuildingId(buildingId: BuildingId): List<Apartment> =
        jpaRepository.findAllByBuildingId(buildingId.value).map(ApartmentEntityMapper::toDomain)

    override fun existsById(id: ApartmentId): Boolean =
        jpaRepository.existsById(id.value)

    override fun existsByBuildingId(buildingId: BuildingId): Boolean =
        jpaRepository.existsByBuildingId(buildingId.value)

    override fun deleteById(id: ApartmentId) =
        jpaRepository.deleteById(id.value)
}
