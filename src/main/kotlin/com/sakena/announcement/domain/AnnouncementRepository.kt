package com.sakena.announcement.domain

import com.sakena.announcement.domain.model.Announcement
import com.sakena.property.domain.model.BuildingId

/**
 * Outbound port for persisting announcements. Declared in the domain layer and
 * implemented by an adapter in infrastructure — this is the dependency
 * inversion that keeps the domain ignorant of JPA, SQL and Spring.
 */
interface AnnouncementRepository {
    fun save(announcement: Announcement): Announcement

    /** One building's announcements, newest first. */
    fun findAllByBuildingNewestFirst(buildingId: BuildingId): List<Announcement>
}
