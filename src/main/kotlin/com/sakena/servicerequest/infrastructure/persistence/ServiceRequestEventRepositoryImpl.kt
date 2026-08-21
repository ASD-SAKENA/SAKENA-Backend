package com.sakena.servicerequest.infrastructure.persistence

import com.sakena.servicerequest.domain.ServiceRequestEvent
import com.sakena.servicerequest.domain.ServiceRequestEventRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ServiceRequestEventRepositoryImpl(
    private val jpa: JpaServiceRequestEventRepository,
) : ServiceRequestEventRepository {
    override fun save(event: ServiceRequestEvent): ServiceRequestEvent {
        val entity = ServiceRequestEventJpaEntity(
            id = event.id,
            serviceRequestId = event.serviceRequestId.value,
            eventType = event.type,
            occurredAt = event.occurredAt,
            performedBy = event.performedBy?.value,
            payload = event.payload,
        )
        val saved = jpa.save(entity)
        return event.copy(id = saved.id)
    }
}
