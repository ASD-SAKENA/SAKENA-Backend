package com.sakena.user.infrastructure.web

import com.sakena.user.application.JwtTokenProvider
import com.sakena.user.application.StaffDirectoryService
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.security.AuthRateLimitFilter
import com.sakena.user.infrastructure.security.JwtAuthenticationFilter
import com.sakena.user.infrastructure.security.SecurityConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(StaffController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    AuthRateLimitFilter::class,
)
class StaffAuthorizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var staffDirectoryService: StaffDirectoryService

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockkBean
    private lateinit var userRepository: UserRepository

    @MockkBean
    private lateinit var ratingService: com.sakena.rating.application.RatingService

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager may list active staff`() {
        every { staffDirectoryService.getActiveStaff() } returns emptyList()
        every { ratingService.getAverageFor(emptyList()) } returns emptyMap()

        mockMvc.perform(get("/api/v1/staff"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager sees each staff member's average rating`() {
        val staff = com.sakena.user.domain.User.register(
            username = "electrician",
            email = "electrician@sakena.test",
            rawPassword = "password123",
            passwordEncoder = { it },
            role = com.sakena.user.domain.Role.STAFF,
        )
        every { staffDirectoryService.getActiveStaff() } returns listOf(staff)
        every { ratingService.getAverageFor(listOf(staff.id)) } returns mapOf(staff.id to 4.5)

        mockMvc.perform(get("/api/v1/staff"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].averageRating").value(4.5))
    }

    @Test
    @WithMockUser(username = "resident", roles = ["RESIDENT"])
    fun `resident may not list staff`() {
        mockMvc.perform(get("/api/v1/staff"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "staff", roles = ["STAFF"])
    fun `staff may not list staff`() {
        mockMvc.perform(get("/api/v1/staff"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "admin", roles = ["ADMIN"])
    fun `admin may not list staff through this manager-only endpoint`() {
        mockMvc.perform(get("/api/v1/staff"))
            .andExpect(status().isForbidden)
    }
}
