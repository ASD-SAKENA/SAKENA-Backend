package com.sakena.announcement.application

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.user.domain.UserId
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
) {

    fun create(command: CreateAnnouncementCommand, createdBy: UserId): Announcement {
        val announcement = Announcement.create(command.title, command.body, createdBy)
        return announcementRepository.save(announcement)
    }

    @Transactional(readOnly = true)
    fun getAll(): List<Announcement> = announcementRepository.findAllNewestFirst()
}
