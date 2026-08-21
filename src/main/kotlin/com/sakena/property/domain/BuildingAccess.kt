package com.sakena.property.domain

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId

/**
 * Resolves the building boundary from a trusted authenticated identity.
 * Application services use this port instead of accepting a caller-supplied
 * building as proof of access.
 */
interface BuildingAccess {
    /** Resolves the building visible to an already-authenticated user. */
    fun buildingIdFor(user: User): BuildingId? =
        when (user.role) {
            Role.MANAGER -> managedBuildingId(user.id)
            Role.RESIDENT -> residentBuildingId(user.id)
            Role.STAFF, Role.ADMIN -> null
        }

    fun managedBuildingId(managerId: UserId): BuildingId

    fun residentBuildingId(residentId: UserId): BuildingId

    fun requireManagerAccess(buildingId: BuildingId, managerId: UserId)

    fun requireResidentAccess(buildingId: BuildingId, residentId: UserId)
}
