package com.sakena.announcement.infrastructure.persistence

import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.property.domain.model.BuildingId
import org.springframework.stereotype.Component

/**
 * Adapter implementing the domain [AnnouncementRepository] port on top of
 * Spring Data JPA. This is the only place that knows about
 * [AnnouncementEntity] and [AnnouncementJpaRepository].
 */
@Component
class AnnouncementRepositoryAdapter(
    private val jpaRepository: AnnouncementJpaRepository,
) : AnnouncementRepository {

    override fun save(announcement: Announcement): Announcement {
        val saved = jpaRepository.save(AnnouncementEntityMapper.toEntity(announcement))
        return AnnouncementEntityMapper.toDomain(saved)
    }

    override fun findAllByBuildingNewestFirst(buildingId: BuildingId): List<Announcement> =
        jpaRepository.findAllByBuildingIdOrderByCreatedAtDesc(buildingId.value)
            .map(AnnouncementEntityMapper::toDomain)
}
