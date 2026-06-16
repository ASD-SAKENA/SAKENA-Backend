package com.sakena.task.application.command

import com.sakena.task.domain.model.TaskStatus

/**
 * Application-layer input models. They decouple the use cases from the web DTOs,
 * so the API contract can evolve independently of the service signatures.
 */
data class CreateTaskCommand(
    val title: String,
    val description: String?,
)

data class UpdateTaskCommand(
    val title: String,
    val description: String?,
)

data class ChangeTaskStatusCommand(
    val status: TaskStatus,
)
