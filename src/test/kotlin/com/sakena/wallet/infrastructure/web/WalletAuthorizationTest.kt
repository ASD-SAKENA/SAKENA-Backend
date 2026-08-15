package com.sakena.wallet.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sakena.user.application.JwtTokenProvider
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.security.AuthRateLimitFilter
import com.sakena.user.infrastructure.security.JwtAuthenticationFilter
import com.sakena.user.infrastructure.security.SecurityConfig
import com.sakena.wallet.application.WalletService
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.infrastructure.web.dto.FundWalletRequest
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(WalletController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    AuthRateLimitFilter::class,
)
class WalletAuthorizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var walletService: WalletService

    @MockkBean
    private lateinit var profileService: ProfileService

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockkBean
    private lateinit var userRepository: UserRepository

    @Test
    @WithMockUser(username = "resident", roles = ["RESIDENT"])
    fun `resident may fund their wallet`() {
        val resident = user("resident", Role.RESIDENT)
        val funded = Wallet.createForUser(resident.id).apply { credit(BigDecimal("500000")) }
        every { profileService.getUserByUsername("resident") } returns resident
        every { walletService.fundMyWallet(any(), resident.id) } returns funded

        mockMvc.perform(validFundingRequest())
            .andExpect(status().isCreated)

        verify(exactly = 1) { walletService.fundMyWallet(any(), resident.id) }
    }

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager may not fund a personal wallet`() {
        mockMvc.perform(validFundingRequest())
            .andExpect(status().isForbidden)

        verify(exactly = 0) { walletService.fundMyWallet(any(), any()) }
    }

    @Test
    @WithMockUser(username = "staff", roles = ["STAFF"])
    fun `staff may not fund a personal wallet`() {
        mockMvc.perform(validFundingRequest())
            .andExpect(status().isForbidden)

        verify(exactly = 0) { walletService.fundMyWallet(any(), any()) }
    }

    private fun validFundingRequest() =
        post("/api/v1/wallets/me/top-ups")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(FundWalletRequest(BigDecimal("500000"))))

    private fun user(username: String, role: Role): User {
        val now = Instant.parse("2026-01-15T10:00:00Z")
        return User.reconstitute(
            id = UserId.generate(),
            username = username,
            email = "$username@example.com",
            passwordHash = "hash",
            role = role,
            createdAt = now,
            updatedAt = now,
            active = true,
        )
    }
}
