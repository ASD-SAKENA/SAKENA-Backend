package com.sakena.membership.domain

import com.sakena.membership.domain.model.StaffBuildingMembership
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId

/** Persistence port for the one-building assignment of service staff. */
interface StaffBuildingMembershipRepository {
    fun save(membership: StaffBuildingMembership): StaffBuildingMembership

    fun findByStaffId(staffId: UserId): StaffBuildingMembership?

    fun findAllByBuilding(buildingId: BuildingId): List<StaffBuildingMembership>
}
