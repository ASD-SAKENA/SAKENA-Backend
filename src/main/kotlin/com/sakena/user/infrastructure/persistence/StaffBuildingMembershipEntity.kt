package com.sakena.user.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA persistence model for the buildings a staff member serves. */
@Entity
@Table(name = "staff_building_memberships")
class StaffBuildingMembershipEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "staff_id", nullable = false, updatable = false)
    var staffId: UUID,

    @Column(name = "building_id", nullable = false, updatable = false)
    var buildingId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
