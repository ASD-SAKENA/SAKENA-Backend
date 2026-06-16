package com.sakena.task.infrastructure.persistence

import com.sakena.task.domain.model.Task
import com.sakena.task.domain.model.TaskId

/** Translates between the domain aggregate and its JPA representation. */
internal object TaskEntityMapper {

    fun toEntity(task: Task): TaskEntity =
        TaskEntity(
            id = task.id.value,
            title = task.title,
            description = task.description,
            status = task.status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )

    fun toDomain(entity: TaskEntity): Task =
        Task.reconstitute(
            id = TaskId(entity.id),
            title = entity.title,
            description = entity.description,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
