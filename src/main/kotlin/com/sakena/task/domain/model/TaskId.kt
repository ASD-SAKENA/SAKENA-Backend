package com.sakena.task.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

/**
 * Value object identifying a [Task] aggregate. Wrapping the raw [UUID] keeps the
 * type system honest — a `TaskId` can never be mixed up with some other id.
 */
@JvmInline
value class TaskId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): TaskId = TaskId(UUID.randomUUID())

        fun from(raw: String): TaskId =
            try {
                TaskId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid task id")
            }
    }
}
