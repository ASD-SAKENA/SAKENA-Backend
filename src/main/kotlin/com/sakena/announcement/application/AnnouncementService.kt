package com.sakena.announcement.application

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
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
) {

    fun create(command: CreateAnnouncementCommand, createdBy: User): Announcement {
        if (createdBy.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can publish announcements")
        }
        val announcement = Announcement.create(
            command.title,
            command.body,
            createdBy.id,
            buildingAccess.managedBuildingId(createdBy.id),
        )
        return announcementRepository.save(announcement)
    }

    @Transactional(readOnly = true)
    fun getAll(viewer: User): List<Announcement> =
        announcementRepository.findAllByBuildingNewestFirst(buildingIdFor(viewer))

    private fun buildingIdFor(user: User): BuildingId = when (user.role) {
        Role.MANAGER -> buildingAccess.managedBuildingId(user.id)
        Role.RESIDENT -> buildingAccess.residentBuildingId(user.id)
        Role.STAFF, Role.ADMIN -> throw DomainForbiddenException("You cannot access building announcements")
    }
}
