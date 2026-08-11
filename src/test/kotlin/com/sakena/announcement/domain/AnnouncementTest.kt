package com.sakena.announcement.domain

import com.sakena.announcement.domain.model.Announcement
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnnouncementTest {

    private val author = UserId.generate()

    @Test
    fun `create trims title and body`() {
        val announcement = Announcement.create("  Water outage  ", "  Details here  ", author)

        assertEquals("Water outage", announcement.title)
        assertEquals("Details here", announcement.body)
        assertEquals(author, announcement.createdBy)
    }

    @Test
    fun `create rejects a blank title`() {
        assertFailsWith<DomainValidationException> {
            Announcement.create("   ", "body", author)
        }
    }

    @Test
    fun `create rejects a blank body`() {
        assertFailsWith<DomainValidationException> {
            Announcement.create("title", "   ", author)
        }
    }

    @Test
    fun `create rejects an overlong title`() {
        assertFailsWith<DomainValidationException> {
            Announcement.create("x".repeat(Announcement.MAX_TITLE_LENGTH + 1), "body", author)
        }
    }

    @Test
    fun `create rejects an overlong body`() {
        assertFailsWith<DomainValidationException> {
            Announcement.create("title", "x".repeat(Announcement.MAX_BODY_LENGTH + 1), author)
        }
    }
}
