package com.sakena.property.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "apartments")
class ApartmentEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "building_id", nullable = false)
    var buildingId: UUID,

    @Column(name = "unit_number", nullable = false, length = 50)
    var unitNumber: String,

    @Column(name = "floor_number", nullable = false)
    var floorNumber: Int,

    @Column(name = "area_square_meters", nullable = false, precision = 10, scale = 2)
    var areaSquareMeters: BigDecimal,

    @Column(name = "bedrooms", nullable = false)
    var bedrooms: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
