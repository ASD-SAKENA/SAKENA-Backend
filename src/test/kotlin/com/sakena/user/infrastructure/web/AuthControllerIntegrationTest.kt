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

    @Test
    fun `register with a duplicate username returns 409 with the conflicting field`() {
        val suffix = UUID.randomUUID().toString()
        val existingUser = User.register(
            username = "existing-$suffix",
            email = "existing-$suffix@example.com",
            rawPassword = VALID_PASSWORD,
            passwordEncoder = passwordEncoder::encode,
        )
        userRepository.save(existingUser)
        val request = RegisterRequest(
            username = existingUser.username,
            email = "new-$suffix@example.com",
            password = VALID_PASSWORD,
        )

        mockMvc.perform(
            post(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("User with username '${existingUser.username}' already exists"))
            .andExpect(jsonPath("$.path").value(REGISTER_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    @Test
    fun `register with a duplicate email returns 409 with the conflicting field`() {
        val suffix = UUID.randomUUID().toString()
        val existingUser = User.register(
            username = "existing-$suffix",
            email = "existing-$suffix@example.com",
            rawPassword = VALID_PASSWORD,
            passwordEncoder = passwordEncoder::encode,
        )
        userRepository.save(existingUser)
        val request = RegisterRequest(
            username = "new-$suffix",
            email = existingUser.email,
            password = VALID_PASSWORD,
        )

        mockMvc.perform(
            post(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("User with email '${existingUser.email}' already exists"))
            .andExpect(jsonPath("$.path").value(REGISTER_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    @Test
    fun `reset password with an invalid token returns 400 with a safe reason`() {
        val request = ResetPasswordRequest(
            token = "invalid-${UUID.randomUUID()}",
            newPassword = "new-valid-password",
        )

        mockMvc.perform(
            post(RESET_PASSWORD_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Invalid or expired reset token"))
            .andExpect(jsonPath("$.path").value(RESET_PASSWORD_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    @Test
    fun `register with an unsupported role returns 400 with a clear reason`() {
        val suffix = UUID.randomUUID().toString()
        val request = RegisterRequest(
            username = "invalid-role-$suffix",
            email = "invalid-role-$suffix@example.com",
            password = VALID_PASSWORD,
            role = "OWNER",
        )

        mockMvc.perform(
            post(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Invalid role 'OWNER'"))
            .andExpect(jsonPath("$.path").value(REGISTER_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    @Test
    fun `login with malformed JSON returns 400 without exposing parser details`() {
        val malformedBody = """{"username":"resident","password":"""

        mockMvc.perform(
            post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedBody),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Malformed request body"))
            .andExpect(jsonPath("$.path").value(LOGIN_PATH))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors").isEmpty)
    }

    private companion object {
        const val VALID_PASSWORD = "valid-password"
        const val LOGIN_PATH = "/api/v1/auth/login"
        const val REGISTER_PATH = "/api/v1/auth/register"
        const val RESET_PASSWORD_PATH = "/api/v1/auth/reset-password"
    }
}
