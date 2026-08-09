package com.sakena.facility.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

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

    @Column(name = "capacity", nullable = false)
    var capacity: Int,

    @Column(name = "opens_at", nullable = false)
    var opensAt: LocalTime,

    @Column(name = "closes_at", nullable = false)
    var closesAt: LocalTime,

    /** Comma-separated DayOfWeek names; empty when the facility never closes. */
    @Column(name = "closed_days", nullable = false, length = 100)
    var closedDays: String,

    @Column(name = "min_duration_minutes", nullable = false)
    var minDurationMinutes: Int,

    @Column(name = "max_duration_minutes", nullable = false)
    var maxDurationMinutes: Int,

    @Column(name = "max_advance_days", nullable = false)
    var maxAdvanceDays: Int,

    @Column(name = "max_per_resident_per_week", nullable = false)
    var maxPerResidentPerWeek: Int,

    @Column(name = "hourly_price", nullable = false, precision = 18, scale = 2)
    var hourlyPrice: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
