package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.servicerequest.domain.ServiceRequestStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "service_requests")
class ServiceRequestJpaEntity(
    @Id
    val id: UUID,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, length = 2000)
    var description: String,

    @Column
    var location: String?,

    @Column(name = "created_by", nullable = false)
    var createdBy: UUID,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ServiceRequestStatus,

    @Column(name = "assigned_to")
    var assignedTo: UUID?,

    @Column(name = "resolved_at")
    var resolvedAt: Instant?
)
