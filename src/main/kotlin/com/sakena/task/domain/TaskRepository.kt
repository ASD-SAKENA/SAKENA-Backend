package com.sakena.task.domain

import com.sakena.task.domain.model.Task
import com.sakena.task.domain.model.TaskId

/**
 * Outbound port for persisting tasks. Declared in the domain layer and
 * implemented by an adapter in infrastructure — this is the dependency
 * inversion that keeps the domain ignorant of JPA, SQL and Spring.
 */
interface TaskRepository {
    fun save(task: Task): Task

    fun findById(id: TaskId): Task?

    fun findAll(): List<Task>

    fun existsById(id: TaskId): Boolean

    fun deleteById(id: TaskId)
}
