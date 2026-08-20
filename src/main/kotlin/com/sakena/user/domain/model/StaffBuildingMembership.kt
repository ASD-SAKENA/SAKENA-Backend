package com.sakena.user.domain.model

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

/** Value object identifying a [StaffBuildingMembership]. */
@JvmInline
value class StaffBuildingMembershipId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): StaffBuildingMembershipId = StaffBuildingMembershipId(UUID.randomUUID())

        fun from(raw: String): StaffBuildingMembershipId =
            try {
                StaffBuildingMembershipId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid staff membership id")
            }
    }
}

/**
 * A staff member's link to a building they serve.
 *
 * Staff are not tied to a unit the way residents are, so this is what puts
 * them inside a building's boundary: without it a manager could see — and
 * assign work to — every staff account in the deployment.
 */
class StaffBuildingMembership private constructor(
    val id: StaffBuildingMembershipId,
    val staffId: UserId,
    val buildingId: BuildingId,
    val createdAt: Instant,
) {
    companion object {
        fun grant(
            staffId: UserId,
            buildingId: BuildingId,
            createdAt: Instant = Instant.now(),
        ): StaffBuildingMembership = StaffBuildingMembership(
            id = StaffBuildingMembershipId.new(),
            staffId = staffId,
            buildingId = buildingId,
            createdAt = createdAt,
        )

        /** Rebuilds from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: StaffBuildingMembershipId,
            staffId: UserId,
            buildingId: BuildingId,
            createdAt: Instant,
        ): StaffBuildingMembership = StaffBuildingMembership(id, staffId, buildingId, createdAt)
    }
}
