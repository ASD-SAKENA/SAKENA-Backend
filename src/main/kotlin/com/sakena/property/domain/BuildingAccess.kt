package com.sakena.property.domain

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId

/**
 * Resolves the building boundary from a trusted authenticated identity.
 * Application services use this port instead of accepting a caller-supplied
 * building as proof of access.
 */
interface BuildingAccess {
    fun managedBuildingId(managerId: UserId): BuildingId

    fun residentBuildingId(residentId: UserId): BuildingId

    fun staffBuildingId(staffId: UserId): BuildingId

    fun requireManagerAccess(buildingId: BuildingId, managerId: UserId)

    fun requireResidentAccess(buildingId: BuildingId, residentId: UserId)

    fun requireStaffAccess(buildingId: BuildingId, staffId: UserId)
}
