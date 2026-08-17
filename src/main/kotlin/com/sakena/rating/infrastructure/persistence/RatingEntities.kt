package com.sakena.rating.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "staff_ratings")
class StaffRatingEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "service_request_id", nullable = false, updatable = false)
    var serviceRequestId: UUID,

    @Column(name = "staff_id", nullable = false, updatable = false)
    var staffId: UUID,

    @Column(name = "resident_id", nullable = false, updatable = false)
    var residentId: UUID,

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "score", nullable = false, updatable = false)
    var score: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
