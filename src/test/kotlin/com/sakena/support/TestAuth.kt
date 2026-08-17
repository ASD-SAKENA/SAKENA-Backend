package com.sakena.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.user.infrastructure.web.RegisterRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Registers a user over HTTP and returns the JWT. Integration tests that hit
 * secured endpoints need this — MockMvc does not bypass Spring Security.
 */
object TestAuth {
    data class RegisteredManager(val token: String, val buildingId: String)

    fun register(
        mockMvc: MockMvc,
        objectMapper: ObjectMapper,
        role: String = "MANAGER",
        usernamePrefix: String = "user",
    ): String {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "$usernamePrefix-$suffix"
        val request = RegisterRequest(
            username = username,
            email = "$username@example.com",
            password = "password123",
            role = role,
        )
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString).get("token").asText()
    }

    /**
     * Registers a manager and resolves the building auto-created for them at
     * registration — there is no create-building endpoint, so this is the
     * only way an integration test gets a real buildingId to work with.
     */
    fun registerManagerWithBuilding(
        mockMvc: MockMvc,
        objectMapper: ObjectMapper,
        usernamePrefix: String = "mgr",
    ): RegisteredManager {
        val token = register(mockMvc, objectMapper, role = "MANAGER", usernamePrefix = usernamePrefix)
        val profile = mockMvc.perform(
            get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(token)),
        )
            .andExpect(status().isOk)
            .andReturn()

        val buildingId = objectMapper.readTree(profile.response.contentAsString)
            .get("managedBuildingId").asText()
        return RegisteredManager(token, buildingId)
    }

    fun bearer(token: String) = "Bearer $token"

    fun authHeader(token: String): Pair<String, String> =
        HttpHeaders.AUTHORIZATION to bearer(token)
}
