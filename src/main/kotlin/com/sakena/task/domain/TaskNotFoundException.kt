package com.sakena.task.domain

import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.task.domain.model.TaskId

class TaskNotFoundException(id: TaskId) :
    EntityNotFoundException("Task with id '$id' was not found")
