package com.sakena.announcement.application

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.AnnouncementRepository
import com.sakena.announcement.domain.model.Announcement
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AnnouncementServiceTest {

    private val repository = mockk<AnnouncementRepository>()
    private val service = AnnouncementService(repository)

    @Test
    fun `create persists a new announcement for the author`() {
        val author = UserId.generate()
        val saved = slot<Announcement>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateAnnouncementCommand("Water outage", "Details"), author)

        assertEquals("Water outage", result.title)
        assertEquals(author, result.createdBy)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `getAll returns announcements newest first from the port`() {
        val newest = Announcement.create("New", "body", UserId.generate())
        val oldest = Announcement.create("Old", "body", UserId.generate())
        every { repository.findAllNewestFirst() } returns listOf(newest, oldest)

        val result = service.getAll()

        assertEquals(listOf(newest, oldest), result)
    }
}
