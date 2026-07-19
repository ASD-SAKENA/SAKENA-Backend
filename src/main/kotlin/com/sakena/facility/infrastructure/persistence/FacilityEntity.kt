package com.sakena.facility.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA persistence model. Deliberately separate from the domain
 * [com.sakena.facility.domain.model.Facility] so database/ORM concerns never
 * leak into the domain.
 */
@Entity
@Table(name = "facilities")
class FacilityEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @Column(name = "icon", length = 50)
    var icon: String?,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
