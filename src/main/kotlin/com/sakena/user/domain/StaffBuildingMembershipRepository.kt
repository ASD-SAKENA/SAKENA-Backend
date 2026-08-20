package com.sakena.user.domain

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.model.StaffBuildingMembership

/** Outbound port for the buildings each staff member serves. */
interface StaffBuildingMembershipRepository {
    fun save(membership: StaffBuildingMembership): StaffBuildingMembership

    /** Staff ids serving the given building. */
    fun findStaffIdsByBuilding(buildingId: BuildingId): List<UserId>

    fun exists(staffId: UserId, buildingId: BuildingId): Boolean
}
