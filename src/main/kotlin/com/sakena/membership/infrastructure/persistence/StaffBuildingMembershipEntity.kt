package com.sakena.membership.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "staff_building_memberships")
class StaffBuildingMembershipEntity(
    @Id
    @Column(name = "staff_id", nullable = false, updatable = false)
    var staffId: UUID,

    @Column(name = "building_id", nullable = false, updatable = false)
    var buildingId: UUID,

    @Column(name = "joined_at", nullable = false, updatable = false)
    var joinedAt: Instant,
)
