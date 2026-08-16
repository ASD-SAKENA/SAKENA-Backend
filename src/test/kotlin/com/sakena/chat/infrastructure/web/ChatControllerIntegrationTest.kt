package com.sakena.chat.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.chat.infrastructure.web.dto.SendMessageRequest
import com.sakena.support.TestAuth
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Exercises chat over a real Postgres (via [IntegrationTest]'s Testcontainers
 * setup) rather than a mocked repository — the first-page listing regressed
 * in production with a Postgres-only "could not determine data type of
 * parameter" error that unit tests mocking the repository never exercised.
 */
@AutoConfigureMockMvc
class ChatControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private lateinit var managerToken: String
    private lateinit var buildingId: String

    @BeforeEach
    fun setUp() {
        val manager = TestAuth.registerManagerWithBuilding(mockMvc, objectMapper, usernamePrefix = "chat-mgr")
        managerToken = manager.token
        buildingId = manager.buildingId
    }

    @Test
    fun `listing an empty building chat with no before cursor succeeds`() {
        mockMvc.perform(
            get("/api/v1/buildings/$buildingId/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(managerToken)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `sending then listing the first page with no before cursor succeeds`() {
        mockMvc.perform(
            post("/api/v1/buildings/$buildingId/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(SendMessageRequest("Hello building"))),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/buildings/$buildingId/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(managerToken)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].body").value("Hello building"))
    }

    @Test
    fun `paging further back with an explicit before cursor succeeds`() {
        mockMvc.perform(
            post("/api/v1/buildings/$buildingId/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(SendMessageRequest("First message"))),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/buildings/$buildingId/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(managerToken))
                .param("before", java.time.Instant.now().plusSeconds(60).toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }
}
