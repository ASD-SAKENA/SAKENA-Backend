package com.sakena.servicerequest.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JpaServiceRequestEventRepository : JpaRepository<ServiceRequestEventJpaEntity, UUID>
