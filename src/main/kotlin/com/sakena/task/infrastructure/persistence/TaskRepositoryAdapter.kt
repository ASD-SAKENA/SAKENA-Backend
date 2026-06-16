package com.sakena.task.infrastructure.persistence

import com.sakena.task.domain.TaskRepository
import com.sakena.task.domain.model.Task
import com.sakena.task.domain.model.TaskId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Inbound adapter implementing the domain [TaskRepository] port on top of Spring
 * Data JPA. This is the only place that knows about [TaskEntity] and [TaskJpaRepository].
 */
@Component
class TaskRepositoryAdapter(
    private val jpaRepository: TaskJpaRepository,
) : TaskRepository {

    override fun save(task: Task): Task {
        val saved = jpaRepository.save(TaskEntityMapper.toEntity(task))
        return TaskEntityMapper.toDomain(saved)
    }

    override fun findById(id: TaskId): Task? =
        jpaRepository.findByIdOrNull(id.value)?.let(TaskEntityMapper::toDomain)

    override fun findAll(): List<Task> =
        jpaRepository.findAll().map(TaskEntityMapper::toDomain)

    override fun existsById(id: TaskId): Boolean =
        jpaRepository.existsById(id.value)

    override fun deleteById(id: TaskId) =
        jpaRepository.deleteById(id.value)
}
