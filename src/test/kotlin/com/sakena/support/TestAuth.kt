package com.sakena.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.user.infrastructure.web.RegisterRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Registers a user over HTTP and returns the JWT. Integration tests that hit
 * secured endpoints need this — MockMvc does not bypass Spring Security.
 */
object TestAuth {
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

    fun bearer(token: String) = "Bearer $token"

    fun authHeader(token: String): Pair<String, String> =
        HttpHeaders.AUTHORIZATION to bearer(token)
}
