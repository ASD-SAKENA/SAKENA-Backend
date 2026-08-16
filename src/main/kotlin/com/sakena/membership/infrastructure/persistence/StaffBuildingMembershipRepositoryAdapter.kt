package com.sakena.membership.infrastructure.persistence

import com.sakena.membership.domain.StaffBuildingMembershipRepository
import com.sakena.membership.domain.model.StaffBuildingMembership
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface StaffBuildingMembershipJpaRepository :
    JpaRepository<StaffBuildingMembershipEntity, UUID> {

    fun findAllByBuildingIdOrderByJoinedAtAsc(buildingId: UUID): List<StaffBuildingMembershipEntity>
}

@Component
class StaffBuildingMembershipRepositoryAdapter(
    private val jpaRepository: StaffBuildingMembershipJpaRepository,
) : StaffBuildingMembershipRepository {

    override fun save(membership: StaffBuildingMembership): StaffBuildingMembership {
        jpaRepository.save(membership.toEntity())
        return membership
    }

    override fun findByStaffId(staffId: UserId): StaffBuildingMembership? =
        jpaRepository.findById(staffId.value).orElse(null)?.toDomain()

    override fun findAllByBuilding(buildingId: BuildingId): List<StaffBuildingMembership> =
        jpaRepository.findAllByBuildingIdOrderByJoinedAtAsc(buildingId.value).map { it.toDomain() }

    private fun StaffBuildingMembership.toEntity(): StaffBuildingMembershipEntity =
        StaffBuildingMembershipEntity(
            staffId = staffId.value,
            buildingId = buildingId.value,
            joinedAt = joinedAt,
        )

    private fun StaffBuildingMembershipEntity.toDomain(): StaffBuildingMembership =
        StaffBuildingMembership.reconstitute(
            staffId = UserId(staffId),
            buildingId = BuildingId(buildingId),
            joinedAt = joinedAt,
        )
}
