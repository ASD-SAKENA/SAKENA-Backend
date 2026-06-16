package com.sakena.task.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data JPA repository — an implementation detail of the persistence adapter. */
interface TaskJpaRepository : JpaRepository<TaskEntity, UUID>
