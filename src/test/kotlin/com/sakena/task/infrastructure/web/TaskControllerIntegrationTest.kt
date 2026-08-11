package com.sakena.task.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.support.TestAuth
import com.sakena.task.domain.model.TaskStatus
import com.sakena.task.infrastructure.web.dto.ChangeTaskStatusRequest
import com.sakena.task.infrastructure.web.dto.CreateTaskRequest
import com.sakena.task.infrastructure.web.dto.UpdateTaskRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class TaskControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private lateinit var token: String

    @BeforeEach
    fun registerUser() {
        token = TestAuth.register(mockMvc, objectMapper, role = "MANAGER", usernamePrefix = "task-user")
    }

    @Test
    fun `full task lifecycle over HTTP`() {
        val createBody = objectMapper.writeValueAsString(CreateTaskRequest("Write README", "for the team"))
        val created = mockMvc.perform(
            post("/api/v1/tasks")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Write README"))
            .andExpect(jsonPath("$.status").value("TODO"))
            .andReturn()

        val id = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(
            get("/api/v1/tasks/$id")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))

        val updateBody = objectMapper.writeValueAsString(UpdateTaskRequest("Write great README", "updated"))
        mockMvc.perform(
            put("/api/v1/tasks/$id")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Write great README"))

        val statusBody = objectMapper.writeValueAsString(ChangeTaskStatusRequest(TaskStatus.IN_PROGRESS))
        mockMvc.perform(
            patch("/api/v1/tasks/$id/status")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))

        mockMvc.perform(
            delete("/api/v1/tasks/$id")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/tasks/$id")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `creating a task with a blank title returns 400 with field errors`() {
        val body = objectMapper.writeValueAsString(CreateTaskRequest("", null))
        mockMvc.perform(
            post("/api/v1/tasks")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
    }
}
