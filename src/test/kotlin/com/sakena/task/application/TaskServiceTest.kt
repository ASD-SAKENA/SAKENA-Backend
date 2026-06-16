package com.sakena.task.application

import com.sakena.task.application.command.CreateTaskCommand
import com.sakena.task.application.command.UpdateTaskCommand
import com.sakena.task.domain.TaskNotFoundException
import com.sakena.task.domain.TaskRepository
import com.sakena.task.domain.model.Task
import com.sakena.task.domain.model.TaskId
import com.sakena.task.domain.model.TaskStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskServiceTest {

    private val repository = mockk<TaskRepository>()
    private val service = TaskService(repository)

    @Test
    fun `create persists a new TODO task`() {
        val saved = slot<Task>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateTaskCommand("Buy milk", null))

        assertEquals("Buy milk", result.title)
        assertEquals(TaskStatus.TODO, result.status)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `getById throws when the task is missing`() {
        val id = TaskId.new()
        every { repository.findById(id) } returns null

        assertFailsWith<TaskNotFoundException> { service.getById(id) }
    }

    @Test
    fun `update renames an existing task`() {
        val existing = Task.create("Old", null)
        every { repository.findById(existing.id) } returns existing
        every { repository.save(any()) } answers { firstArg() }

        val result = service.update(existing.id, UpdateTaskCommand("New", "desc"))

        assertEquals("New", result.title)
        assertEquals("desc", result.description)
    }

    @Test
    fun `delete throws when the task does not exist`() {
        val id = TaskId.new()
        every { repository.existsById(id) } returns false

        assertFailsWith<TaskNotFoundException> { service.delete(id) }
        verify(exactly = 0) { repository.deleteById(any()) }
    }
}
