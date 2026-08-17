package com.sakena.user.infrastructure.web

import com.sakena.user.application.JwtTokenProvider
import com.sakena.user.application.UserAdminService
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.security.AuthRateLimitFilter
import com.sakena.user.infrastructure.security.JwtAuthenticationFilter
import com.sakena.user.infrastructure.security.SecurityConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(UserController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    AuthRateLimitFilter::class,
)
class UserAuthorizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userAdminService: UserAdminService

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockkBean
    private lateinit var userRepository: UserRepository

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager may not list system users`() {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { userAdminService.getUsers(any()) }
    }

    @Test
    @WithMockUser(username = "resident", roles = ["RESIDENT"])
    fun `resident may not list system users`() {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { userAdminService.getUsers(any()) }
    }

    @Test
    @WithMockUser(username = "staff", roles = ["STAFF"])
    fun `staff may not list system users`() {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { userAdminService.getUsers(any()) }
    }
}
