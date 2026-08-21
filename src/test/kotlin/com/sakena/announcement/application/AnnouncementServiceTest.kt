package com.sakena.announcement.application

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.notification.application.NotificationService
import com.sakena.notification.domain.model.NotificationType
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnnouncementServiceTest {

    private val repository = mockk<AnnouncementRepository>()
    private val buildingId = BuildingId.new()
    private val buildingAccess = object : BuildingAccess {
        override fun managedBuildingId(managerId: UserId): BuildingId = buildingId

        override fun residentBuildingId(residentId: UserId): BuildingId = buildingId

        override fun requireManagerAccess(buildingId: BuildingId, managerId: UserId) = Unit

        override fun requireResidentAccess(buildingId: BuildingId, residentId: UserId) = Unit
    }
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val service = AnnouncementService(repository, buildingAccess, notificationService)

    @Test
    fun `create persists a new announcement for the author`() {
        val author = user(Role.MANAGER)
        val saved = slot<Announcement>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateAnnouncementCommand("Water outage", "Details"), author)

        assertEquals("Water outage", result.title)
        assertEquals(author.id, result.createdBy)
        assertEquals(buildingId, result.buildingId)
        verify(exactly = 1) { repository.save(any()) }
        verify(exactly = 1) {
            notificationService.notifyBuildingResidents(
                buildingId,
                "اطلاعیه جدید",
                "Water outage",
                NotificationType.ANNOUNCEMENT,
                "/announcements",
            )
        }
    }

    @Test
    fun `getAll returns announcements newest first from the port`() {
        val resident = user(Role.RESIDENT)
        val newest = Announcement.create("New", "body", UserId.generate(), buildingId)
        val oldest = Announcement.create("Old", "body", UserId.generate(), buildingId)
        every { repository.findAllByBuildingNewestFirst(buildingId) } returns listOf(newest, oldest)

        val result = service.getAll(resident)

        assertEquals(listOf(newest, oldest), result)
    }

    @Test
    fun `staff cannot read building announcements`() {
        val staff = user(Role.STAFF)

        val exception = assertFailsWith<DomainForbiddenException> { service.getAll(staff) }

        assertEquals("You cannot access building announcements", exception.message)
    }

    @Test
    fun `administrators cannot read building announcements`() {
        val admin = user(Role.ADMIN)

        val exception = assertFailsWith<DomainForbiddenException> { service.getAll(admin) }

        assertEquals("You cannot access building announcements", exception.message)
    }

    private fun user(role: Role): User {
        val id = UserId.generate()
        val now = Instant.now()
        return User.reconstitute(
            id,
            "user-${id.value}",
            "${id.value}@example.com",
            "hash",
            role,
            now,
            now,
            true,
            managedBuildingId = buildingId.takeIf { role == Role.MANAGER },
        )
    }
}
