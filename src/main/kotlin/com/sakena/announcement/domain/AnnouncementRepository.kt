package com.sakena.announcement.domain

import com.sakena.announcement.domain.model.Announcement

/**
 * Outbound port for persisting announcements. Declared in the domain layer and
 * implemented by an adapter in infrastructure — this is the dependency
 * inversion that keeps the domain ignorant of JPA, SQL and Spring.
 */
interface AnnouncementRepository {
    fun save(announcement: Announcement): Announcement

    /** All announcements, newest first — the order residents read them in. */
    fun findAllNewestFirst(): List<Announcement>
}
