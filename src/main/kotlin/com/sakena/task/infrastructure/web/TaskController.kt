package com.sakena.task.infrastructure.web

import com.sakena.task.application.TaskService
import com.sakena.task.domain.model.TaskId
import com.sakena.task.infrastructure.web.dto.ChangeTaskStatusRequest
import com.sakena.task.infrastructure.web.dto.CreateTaskRequest
import com.sakena.task.infrastructure.web.dto.TaskResponse
import com.sakena.task.infrastructure.web.dto.UpdateTaskRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * REST adapter for the Task bounded context. Controllers stay thin: parse/validate
 * input, delegate to the application service, map the result back to a DTO.
 */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Create, read, update and delete tasks")
class TaskController(
    private val taskService: TaskService,
) {

    @Operation(summary = "List all tasks")
    @GetMapping
    fun list(): List<TaskResponse> =
        taskService.getAll().map(TaskResponse::from)

    @Operation(summary = "Get a task by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): TaskResponse =
        TaskResponse.from(taskService.getById(TaskId.from(id)))

    @Operation(summary = "Create a new task")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateTaskRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<TaskResponse> {
        val task = taskService.create(request.toCommand())
        val location: URI = uriBuilder.path("/api/v1/tasks/{id}").build(task.id.value)
        return ResponseEntity.created(location).body(TaskResponse.from(task))
    }

    @Operation(summary = "Update a task's title and description")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateTaskRequest,
    ): TaskResponse =
        TaskResponse.from(taskService.update(TaskId.from(id), request.toCommand()))

    @Operation(summary = "Change a task's status")
    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: String,
        @Valid @RequestBody request: ChangeTaskStatusRequest,
    ): TaskResponse =
        TaskResponse.from(taskService.changeStatus(TaskId.from(id), request.toCommand()))

    @Operation(summary = "Delete a task")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) =
        taskService.delete(TaskId.from(id))
}
