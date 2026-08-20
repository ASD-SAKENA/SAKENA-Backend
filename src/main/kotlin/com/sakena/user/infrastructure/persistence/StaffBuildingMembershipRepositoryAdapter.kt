package com.sakena.user.infrastructure.persistence

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.StaffBuildingMembershipRepository
import com.sakena.user.domain.UserId
import com.sakena.user.domain.model.StaffBuildingMembership
import com.sakena.user.domain.model.StaffBuildingMembershipId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface StaffBuildingMembershipJpaRepository :
    JpaRepository<StaffBuildingMembershipEntity, UUID> {

    fun findAllByBuildingId(buildingId: UUID): List<StaffBuildingMembershipEntity>

    fun existsByStaffIdAndBuildingId(staffId: UUID, buildingId: UUID): Boolean
}

/** Adapter implementing the domain [StaffBuildingMembershipRepository] port. */
@Component
class StaffBuildingMembershipRepositoryAdapter(
    private val jpaRepository: StaffBuildingMembershipJpaRepository,
) : StaffBuildingMembershipRepository {

    override fun save(membership: StaffBuildingMembership): StaffBuildingMembership {
        jpaRepository.save(
            StaffBuildingMembershipEntity(
                id = membership.id.value,
                staffId = membership.staffId.value,
                buildingId = membership.buildingId.value,
                createdAt = membership.createdAt,
            ),
        )
        return membership
    }

    override fun findStaffIdsByBuilding(buildingId: BuildingId): List<UserId> =
        jpaRepository.findAllByBuildingId(buildingId.value).map { UserId(it.staffId) }

    override fun exists(staffId: UserId, buildingId: BuildingId): Boolean =
        jpaRepository.existsByStaffIdAndBuildingId(staffId.value, buildingId.value)
}

/** Rebuilds an aggregate from its row, for callers that need the whole record. */
fun StaffBuildingMembershipEntity.toDomain(): StaffBuildingMembership =
    StaffBuildingMembership.reconstitute(
        id = StaffBuildingMembershipId(id),
        staffId = UserId(staffId),
        buildingId = BuildingId(buildingId),
        createdAt = createdAt,
    )
