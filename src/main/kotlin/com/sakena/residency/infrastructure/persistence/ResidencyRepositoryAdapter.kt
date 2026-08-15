package com.sakena.residency.infrastructure.persistence

import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.ResidencyId
import com.sakena.user.domain.UserId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.util.UUID

interface ResidencyJpaRepository : JpaRepository<ResidencyEntity, UUID> {

    fun findByApartmentIdAndMovedOutAtIsNull(apartmentId: UUID): ResidencyEntity?

    fun findByResidentIdAndMovedOutAtIsNull(residentId: UUID): ResidencyEntity?

    fun findAllByApartmentIdOrderByMovedInAtDesc(apartmentId: UUID): List<ResidencyEntity>

    @Query(
        """
        SELECT r FROM ResidencyEntity r
        WHERE r.movedOutAt IS NULL
          AND r.apartmentId IN (
            SELECT a.id FROM ApartmentEntity a WHERE a.buildingId = :buildingId
          )
        """
    )
    fun findActiveByBuilding(@Param("buildingId") buildingId: UUID): List<ResidencyEntity>

    fun findByMovedOutAtIsNull(): List<ResidencyEntity>
}

/**
 * Adapter implementing the domain [ResidencyRepository] port on top of Spring
 * Data JPA. This is the only place that knows about [ResidencyEntity].
 */
@Component
class ResidencyRepositoryAdapter(
    private val jpaRepository: ResidencyJpaRepository,
) : ResidencyRepository {

    override fun save(residency: Residency): Residency {
        jpaRepository.save(toEntity(residency))
        return residency
    }

    override fun findById(id: ResidencyId): Residency? =
        jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findActiveByApartment(apartmentId: ApartmentId): Residency? =
        jpaRepository.findByApartmentIdAndMovedOutAtIsNull(apartmentId.value)?.let(::toDomain)

    override fun findActiveByResident(residentId: UserId): Residency? =
        jpaRepository.findByResidentIdAndMovedOutAtIsNull(residentId.value)?.let(::toDomain)

    override fun findAllByApartment(apartmentId: ApartmentId): List<Residency> =
        jpaRepository.findAllByApartmentIdOrderByMovedInAtDesc(apartmentId.value).map(::toDomain)

    override fun findActiveByBuilding(buildingId: BuildingId): List<Residency> =
        jpaRepository.findActiveByBuilding(buildingId.value).map(::toDomain)

    override fun findAllActive(): List<Residency> =
        jpaRepository.findByMovedOutAtIsNull().map(::toDomain)

    private fun toEntity(residency: Residency): ResidencyEntity =
        ResidencyEntity(
            id = residency.id.value,
            apartmentId = residency.apartmentId.value,
            residentId = residency.residentId.value,
            tenancy = residency.tenancy,
            movedInAt = residency.movedInAt,
            movedOutAt = residency.movedOutAt,
        )

    private fun toDomain(entity: ResidencyEntity): Residency =
        Residency.reconstitute(
            id = ResidencyId(entity.id),
            apartmentId = ApartmentId(entity.apartmentId),
            residentId = UserId(entity.residentId),
            tenancy = entity.tenancy,
            movedInAt = entity.movedInAt,
            movedOutAt = entity.movedOutAt,
        )
}
