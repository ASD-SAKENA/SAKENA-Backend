package com.sakena.task.infrastructure.web.dto

import com.sakena.task.application.command.ChangeTaskStatusCommand
import com.sakena.task.application.command.CreateTaskCommand
import com.sakena.task.application.command.UpdateTaskCommand
import com.sakena.task.domain.model.Task
import com.sakena.task.domain.model.TaskStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateTaskRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,

    @field:Size(max = 2000, message = "description must be at most 2000 characters")
    val description: String? = null,
) {
    fun toCommand() = CreateTaskCommand(title = title, description = description)
}

data class UpdateTaskRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,

    @field:Size(max = 2000, message = "description must be at most 2000 characters")
    val description: String? = null,
) {
    fun toCommand() = UpdateTaskCommand(title = title, description = description)
}

data class ChangeTaskStatusRequest(
    val status: TaskStatus,
) {
    fun toCommand() = ChangeTaskStatusCommand(status = status)
}

data class TaskResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(task: Task) = TaskResponse(
            id = task.id.value,
            title = task.title,
            description = task.description,
            status = task.status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
