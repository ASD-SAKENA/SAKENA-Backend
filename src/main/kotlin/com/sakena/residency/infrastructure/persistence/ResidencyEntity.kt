package com.sakena.residency.infrastructure.persistence

import com.sakena.residency.domain.model.TenancyType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA persistence model for residencies. */
@Entity
@Table(name = "residencies")
class ResidencyEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "apartment_id", nullable = false, updatable = false)
    var apartmentId: UUID,

    @Column(name = "resident_id", nullable = false, updatable = false)
    var residentId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "tenancy", nullable = false, length = 20)
    var tenancy: TenancyType,

    @Column(name = "moved_in_at", nullable = false, updatable = false)
    var movedInAt: Instant,

    @Column(name = "moved_out_at")
    var movedOutAt: Instant?,
)
