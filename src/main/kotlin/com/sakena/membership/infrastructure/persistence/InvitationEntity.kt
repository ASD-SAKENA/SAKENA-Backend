package com.sakena.membership.infrastructure.persistence

import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.membership.domain.model.InvitationStatus
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA persistence model for building invitations. */
@Entity
@Table(name = "building_invitations")
class InvitationEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "building_id", nullable = false, updatable = false)
    var buildingId: UUID,

    @Column(name = "token", nullable = false, unique = true, updatable = false, length = 64)
    var token: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    var channel: InvitationChannel,

    @Column(name = "recipient", length = 200)
    var recipient: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: Role,

    @Column(name = "apartment_id")
    var apartmentId: UUID?,

    @Enumerated(EnumType.STRING)
    @Column(name = "tenancy", length = 20)
    var tenancy: TenancyType?,

    @Column(name = "invited_by", nullable = false, updatable = false)
    var invitedBy: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: InvitationStatus,

    @Column(name = "accepted_by")
    var acceptedBy: UUID?,

    @Column(name = "accepted_at")
    var acceptedAt: Instant?,
)
