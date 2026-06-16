package com.sakena.task.domain.model

/**
 * Lifecycle of a [Task]. The allowed transitions are encoded here so the rule
 * lives with the domain rather than being scattered across services.
 */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    ;

    fun canTransitionTo(target: TaskStatus): Boolean =
        when (this) {
            TODO -> target == IN_PROGRESS || target == DONE
            IN_PROGRESS -> target == DONE || target == TODO
            DONE -> target == IN_PROGRESS
        }
}
