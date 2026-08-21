package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.servicerequest.domain.ServiceRequestEventType
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "service_request_events")
class ServiceRequestEventJpaEntity(
    @Id
    var id: UUID,

    @Column(name = "service_request_id", nullable = false)
    var serviceRequestId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    var eventType: ServiceRequestEventType,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,

    @Column(name = "performed_by")
    var performedBy: UUID?,

    @Column(name = "payload", length = 4000)
    var payload: String?,
)
