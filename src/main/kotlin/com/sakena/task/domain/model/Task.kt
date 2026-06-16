package com.sakena.task.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import java.time.Instant

/**
 * Task aggregate root — a rich domain model that owns its invariants.
 *
 * Construction goes through [create] (new tasks) or [reconstitute] (rehydration
 * from persistence) so that an invalid Task can never exist. State changes go
 * through behaviour methods, never raw setters.
 */
class Task private constructor(
    val id: TaskId,
    title: String,
    description: String?,
    status: TaskStatus,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var title: String = title
        private set

    var description: String? = description
        private set

    var status: TaskStatus = status
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun rename(newTitle: String, newDescription: String?) {
        this.title = validateTitle(newTitle)
        this.description = validateDescription(newDescription)
        touch()
    }

    fun changeStatus(newStatus: TaskStatus) {
        if (newStatus == status) return
        if (!status.canTransitionTo(newStatus)) {
            throw DomainConflictException("Cannot move task from $status to $newStatus")
        }
        this.status = newStatus
        touch()
    }

    private fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 2_000

        fun create(title: String, description: String?): Task {
            val now = Instant.now()
            return Task(
                id = TaskId.new(),
                title = validateTitle(title),
                description = validateDescription(description),
                status = TaskStatus.TODO,
                createdAt = now,
                updatedAt = now,
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: TaskId,
            title: String,
            description: String?,
            status: TaskStatus,
            createdAt: Instant,
            updatedAt: Instant,
        ): Task = Task(id, title, description, status, createdAt, updatedAt)

        private fun validateTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Task title must not be blank")
            if (trimmed.length > MAX_TITLE_LENGTH) {
                throw DomainValidationException("Task title must be at most $MAX_TITLE_LENGTH characters")
            }
            return trimmed
        }

        private fun validateDescription(description: String?): String? {
            val trimmed = description?.trim()?.ifEmpty { null } ?: return null
            if (trimmed.length > MAX_DESCRIPTION_LENGTH) {
                throw DomainValidationException("Task description must be at most $MAX_DESCRIPTION_LENGTH characters")
            }
            return trimmed
        }
    }
}
