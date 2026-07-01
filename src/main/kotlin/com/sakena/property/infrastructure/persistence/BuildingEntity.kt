package com.sakena.property.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "buildings")
class BuildingEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @Column(name = "address", nullable = false, length = 500)
    var address: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
