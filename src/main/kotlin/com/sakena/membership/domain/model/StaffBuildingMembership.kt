package com.sakena.membership.domain.model

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import java.time.Instant

/**
 * Assigns a service-staff user to the single building whose data they may
 * access. The staff user id is the aggregate identity because a staff member
 * can belong to at most one building.
 */
class StaffBuildingMembership private constructor(
    val staffId: UserId,
    val buildingId: BuildingId,
    val joinedAt: Instant,
) {

    companion object {
        fun create(staffId: UserId, buildingId: BuildingId): StaffBuildingMembership =
            StaffBuildingMembership(
                staffId = staffId,
                buildingId = buildingId,
                joinedAt = Instant.now(),
            )

        fun reconstitute(
            staffId: UserId,
            buildingId: BuildingId,
            joinedAt: Instant,
        ): StaffBuildingMembership = StaffBuildingMembership(staffId, buildingId, joinedAt)
    }
}
