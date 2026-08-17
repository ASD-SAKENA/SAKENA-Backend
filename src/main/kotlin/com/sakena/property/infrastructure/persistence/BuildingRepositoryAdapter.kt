package com.sakena.property.infrastructure.persistence

import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BuildingRepositoryAdapter(
    private val jpaRepository: BuildingJpaRepository,
) : BuildingRepository {

    override fun save(building: Building): Building {
        val saved = jpaRepository.save(BuildingEntityMapper.toEntity(building))
        return BuildingEntityMapper.toDomain(saved)
    }

    override fun findById(id: BuildingId): Building? =
        jpaRepository.findByIdOrNull(id.value)?.let(BuildingEntityMapper::toDomain)

    override fun findAll(): List<Building> =
        jpaRepository.findAll().map(BuildingEntityMapper::toDomain)

    override fun existsById(id: BuildingId): Boolean =
        jpaRepository.existsById(id.value)

    override fun deleteById(id: BuildingId) =
        jpaRepository.deleteById(id.value)
}
