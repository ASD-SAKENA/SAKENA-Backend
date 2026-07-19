package com.sakena.facility.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FacilityJpaRepository : JpaRepository<FacilityEntity, UUID>
