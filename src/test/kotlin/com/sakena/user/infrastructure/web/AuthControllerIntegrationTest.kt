package com.sakena.user.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class AuthControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) : IntegrationTest() {

    @Test
    fun `login with invalid credentials returns 401 without revealing which credential failed`() {
        val request = LoginRequest(
            username = "unknown-${UUID.randomUUID()}",
            password = "incorrect-password",
        )

        mockMvc.perform(
            post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Invalid username or password"))
            .andExpect(jsonPath("$.path").value(LOGIN_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    @Test
    fun `login with an inactive account returns 403 with a clear reason`() {
        val suffix = UUID.randomUUID().toString()
        val password = "valid-password"
        val inactiveUser = User.register(
            username = "inactive-$suffix",
            email = "inactive-$suffix@example.com",
            rawPassword = password,
            passwordEncoder = passwordEncoder::encode,
        ).deactivate()
        userRepository.save(inactiveUser)

        val request = LoginRequest(inactiveUser.username, password)

        mockMvc.perform(
            post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message").value("User account is inactive"))
            .andExpect(jsonPath("$.path").value(LOGIN_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    private companion object {
        const val LOGIN_PATH = "/api/v1/auth/login"
    }
}
