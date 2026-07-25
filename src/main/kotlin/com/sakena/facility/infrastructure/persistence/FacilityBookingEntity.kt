package com.sakena.facility.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA persistence model for facility bookings. */
@Entity
@Table(name = "facility_bookings")
class FacilityBookingEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "facility_id", nullable = false)
    var facilityId: UUID,

    @Column(name = "booked_by", nullable = false)
    var bookedBy: UUID,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
