package com.sakena.announcement.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnnouncementJpaRepository : JpaRepository<AnnouncementEntity, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<AnnouncementEntity>
}
