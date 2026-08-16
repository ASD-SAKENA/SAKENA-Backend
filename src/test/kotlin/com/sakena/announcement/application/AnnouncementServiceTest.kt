package com.sakena.announcement.application

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
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

class AnnouncementServiceTest {

    private val repository = mockk<AnnouncementRepository>()
    private val buildingId = BuildingId.new()
    private val buildingAccess = mockk<BuildingAccess>()
    private val service = AnnouncementService(repository, buildingAccess)

    @Test
    fun `create persists a new announcement for the author`() {
        val author = user(Role.MANAGER)
        every { buildingAccess.managedBuildingId(author.id) } returns buildingId
        val saved = slot<Announcement>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateAnnouncementCommand("Water outage", "Details"), author)

        assertEquals("Water outage", result.title)
        assertEquals(author.id, result.createdBy)
        assertEquals(buildingId, result.buildingId)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `getAll returns announcements newest first from the port`() {
        val resident = user(Role.RESIDENT)
        val newest = Announcement.create("New", "body", UserId.generate(), buildingId)
        val oldest = Announcement.create("Old", "body", UserId.generate(), buildingId)
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { repository.findAllByBuildingNewestFirst(buildingId) } returns listOf(newest, oldest)

        val result = service.getAll(resident)

        assertEquals(listOf(newest, oldest), result)
    }

    @Test
    fun `staff reads announcements only from the assigned building`() {
        val staff = user(Role.STAFF)
        val announcement = Announcement.create("Notice", "body", UserId.generate(), buildingId)
        every { buildingAccess.staffBuildingId(staff.id) } returns buildingId
        every { repository.findAllByBuildingNewestFirst(buildingId) } returns listOf(announcement)

        assertEquals(listOf(announcement), service.getAll(staff))
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
        )
    }
}
