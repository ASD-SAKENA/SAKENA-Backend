package com.sakena.wallet.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.shared.web.GlobalExceptionHandler
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.wallet.application.WalletService
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.infrastructure.web.dto.FundWalletRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.math.BigDecimal
import java.time.Instant

class WalletControllerTest {

    private val walletService = mockk<WalletService>()
    private val profileService = mockk<ProfileService>()
    private val objectMapper = jacksonObjectMapper()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(WalletController(walletService, profileService))
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator())
        .build()

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `funding uses the authenticated resident and returns the updated balance`() {
        val resident = authenticateResident()
        val fundedWallet = Wallet.createForUser(resident.id).apply { credit(BigDecimal("250000.50")) }
        every { walletService.fundMyWallet(any(), resident.id) } returns fundedWallet

        mockMvc.perform(
            post("/api/v1/wallets/me/top-ups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(FundWalletRequest(BigDecimal("250000.50")))),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.balance").value(250000.50))

        verify(exactly = 1) {
            walletService.fundMyWallet(match { it.amount == BigDecimal("250000.50") }, resident.id)
        }
    }

    @Test
    fun `funding rejects a non-positive amount before calling the service`() {
        authenticateResident()

        mockMvc.perform(
            post("/api/v1/wallets/me/top-ups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(FundWalletRequest(BigDecimal.ZERO))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))

        verify(exactly = 0) { walletService.fundMyWallet(any(), any()) }
    }

    @Test
    fun `funding rejects amounts that exceed database precision`() {
        authenticateResident()

        mockMvc.perform(
            post("/api/v1/wallets/me/top-ups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(FundWalletRequest(BigDecimal("1.001")))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))

        verify(exactly = 0) { walletService.fundMyWallet(any(), any()) }
    }

    private fun authenticateResident(): User {
        val now = Instant.parse("2026-01-15T10:00:00Z")
        val resident = User.reconstitute(
            id = UserId.generate(),
            username = "resident",
            email = "resident@example.com",
            passwordHash = "hash",
            role = Role.RESIDENT,
            createdAt = now,
            updatedAt = now,
            active = true,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(resident.username, null)
        every { profileService.getUserByUsername(resident.username) } returns resident
        return resident
    }

    private fun validator(): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().also { it.afterPropertiesSet() }
}
