package com.sakena.announcement.infrastructure.persistence

import com.sakena.announcement.domain.model.Announcement
import com.sakena.announcement.domain.model.AnnouncementId
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId

/** Translates between the domain aggregate and its JPA representation. */
internal object AnnouncementEntityMapper {

    fun toEntity(announcement: Announcement): AnnouncementEntity =
        AnnouncementEntity(
            id = announcement.id.value,
            buildingId = announcement.buildingId?.value,
            title = announcement.title,
            body = announcement.body,
            createdBy = announcement.createdBy.value,
            createdAt = announcement.createdAt,
        )

    fun toDomain(entity: AnnouncementEntity): Announcement =
        Announcement.reconstitute(
            id = AnnouncementId(entity.id),
            buildingId = entity.buildingId?.let(::BuildingId),
            title = entity.title,
            body = entity.body,
            createdBy = UserId(entity.createdBy),
            createdAt = entity.createdAt,
        )
}
