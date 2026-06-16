package com.sakena.task.application

import com.sakena.task.application.command.ChangeTaskStatusCommand
import com.sakena.task.application.command.CreateTaskCommand
import com.sakena.task.application.command.UpdateTaskCommand
import com.sakena.task.domain.TaskNotFoundException
import com.sakena.task.domain.TaskRepository
import com.sakena.task.domain.model.Task
import com.sakena.task.domain.model.TaskId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service orchestrating the Task use cases. It owns transaction
 * boundaries and delegates all business rules to the [Task] aggregate. It depends
 * only on the domain [TaskRepository] port — never on infrastructure directly.
 */
@Service
@Transactional
class TaskService(
    private val taskRepository: TaskRepository,
) {

    fun create(command: CreateTaskCommand): Task {
        val task = Task.create(command.title, command.description)
        return taskRepository.save(task)
    }

    fun update(id: TaskId, command: UpdateTaskCommand): Task {
        val task = requireTask(id)
        task.rename(command.title, command.description)
        return taskRepository.save(task)
    }

    fun changeStatus(id: TaskId, command: ChangeTaskStatusCommand): Task {
        val task = requireTask(id)
        task.changeStatus(command.status)
        return taskRepository.save(task)
    }

    fun delete(id: TaskId) {
        if (!taskRepository.existsById(id)) throw TaskNotFoundException(id)
        taskRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: TaskId): Task = requireTask(id)

    @Transactional(readOnly = true)
    fun getAll(): List<Task> = taskRepository.findAll()

    private fun requireTask(id: TaskId): Task =
        taskRepository.findById(id) ?: throw TaskNotFoundException(id)
}
