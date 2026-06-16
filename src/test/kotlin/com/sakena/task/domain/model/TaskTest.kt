package com.sakena.task.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskTest {

    @Test
    fun `create starts a task in TODO with trimmed title`() {
        val task = Task.create("  Write docs  ", "  details  ")

        assertEquals("Write docs", task.title)
        assertEquals("details", task.description)
        assertEquals(TaskStatus.TODO, task.status)
    }

    @Test
    fun `create normalises blank description to null`() {
        val task = Task.create("Title", "   ")
        assertNull(task.description)
    }

    @Test
    fun `create rejects a blank title`() {
        assertFailsWith<DomainValidationException> { Task.create("   ", null) }
    }

    @Test
    fun `create rejects a title that is too long`() {
        val tooLong = "a".repeat(Task.MAX_TITLE_LENGTH + 1)
        assertFailsWith<DomainValidationException> { Task.create(tooLong, null) }
    }

    @Test
    fun `changeStatus follows the allowed lifecycle`() {
        val task = Task.create("Title", null)

        task.changeStatus(TaskStatus.IN_PROGRESS)
        assertEquals(TaskStatus.IN_PROGRESS, task.status)

        task.changeStatus(TaskStatus.DONE)
        assertEquals(TaskStatus.DONE, task.status)
    }

    @Test
    fun `changeStatus rejects an illegal transition`() {
        val task = Task.create("Title", null)
        task.changeStatus(TaskStatus.DONE)

        assertFailsWith<DomainConflictException> { task.changeStatus(TaskStatus.TODO) }
    }

    @Test
    fun `rename updates fields and bumps updatedAt`() {
        val task = Task.create("Old", null)
        val before = task.updatedAt

        task.rename("New title", "New description")

        assertEquals("New title", task.title)
        assertEquals("New description", task.description)
        assertTrue(task.updatedAt >= before)
    }
}
