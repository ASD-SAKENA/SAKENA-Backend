package com.sakena.announcement.application

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.notification.application.NotificationService
import com.sakena.notification.domain.model.NotificationType
import com.sakena.property.domain.BuildingAccess
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service orchestrating the Announcement use cases. It owns
 * transaction boundaries and delegates all business rules to the
 * [Announcement] aggregate, depending only on the domain port.
 */
@Service
@Transactional
class AnnouncementService(
    private val announcementRepository: AnnouncementRepository,
    private val buildingAccess: BuildingAccess,
    private val notificationService: NotificationService,
) {

    fun create(command: CreateAnnouncementCommand, createdBy: User): Announcement {
        if (createdBy.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can publish announcements")
        }
        val buildingId = buildingAccess.managedBuildingId(createdBy.id)
        val announcement = Announcement.create(
            command.title,
            command.body,
            createdBy.id,
            buildingId,
        )
        val saved = announcementRepository.save(announcement)
        notificationService.notifyBuildingResidents(
            buildingId = buildingId,
            title = "اطلاعیه جدید",
            body = saved.title,
            type = NotificationType.ANNOUNCEMENT,
            href = "/announcements",
        )
        return saved
    }

    @Transactional(readOnly = true)
    fun getAll(viewer: User): List<Announcement> {
        if (viewer.role == Role.STAFF || viewer.role == Role.ADMIN) {
            throw DomainForbiddenException("You cannot access building announcements")
        }
        val buildingId = buildingAccess.buildingIdFor(viewer) ?: return emptyList()
        return announcementRepository.findAllByBuildingNewestFirst(buildingId)
    }
}
