package com.sakena.wallet.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.web.RegisterRequest
import com.sakena.wallet.domain.model.TransactionCategory
import com.sakena.wallet.domain.model.TransactionDirection
import com.sakena.wallet.infrastructure.persistence.WalletJpaRepository
import com.sakena.wallet.infrastructure.persistence.WalletTransactionJpaRepository
import com.sakena.wallet.infrastructure.web.dto.FundWalletRequest
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
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@AutoConfigureMockMvc
class WalletControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val walletJpaRepository: WalletJpaRepository,
    @Autowired private val transactionJpaRepository: WalletTransactionJpaRepository,
) : IntegrationTest() {

    @Test
    fun `resident top-ups accumulate in the wallet and persist ledger entries`() {
        val resident = register("wallet-resident", "RESIDENT")

        fund(resident.token, BigDecimal("100000.00"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.balance").value(100000.00))
        fund(resident.token, BigDecimal("25000.50"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.balance").value(125000.50))

        mockMvc.perform(authorizedGet("/api/v1/wallets/me", resident.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(125000.50))

        mockMvc.perform(authorizedGet("/api/v1/wallets/me/transactions", resident.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].direction").value(TransactionDirection.CREDIT.name))
            .andExpect(jsonPath("$[0].category").value(TransactionCategory.WALLET_FUNDING.name))
            .andExpect(jsonPath("$[0].amount").value(25000.50))
            .andExpect(jsonPath("$[0].balanceAfter").value(125000.50))
            .andExpect(jsonPath("$[1].amount").value(100000.00))
            .andExpect(jsonPath("$[1].balanceAfter").value(100000.00))

        val user = assertNotNull(userRepository.findByUsername(resident.username))
        val wallet = assertNotNull(walletJpaRepository.findByOwnerUserId(user.id.value))
        assertEquals(0, wallet.balance.compareTo(BigDecimal("125000.50")))

        val ledger = transactionJpaRepository.findAllByWalletIdOrderByOccurredAtDesc(wallet.id)
        assertEquals(2, ledger.size)
        assertEquals(setOf(TransactionDirection.CREDIT), ledger.map { it.direction }.toSet())
        assertEquals(setOf(TransactionCategory.WALLET_FUNDING), ledger.map { it.category }.toSet())
        assertEquals(
            setOf(BigDecimal("100000.00"), BigDecimal("25000.50")),
            ledger.map { it.amount }.toSet(),
        )
    }

    @Test
    fun `manager cannot fund a personal wallet`() {
        val manager = register("wallet-manager", "MANAGER")

        fund(manager.token, BigDecimal("500000"))
            .andExpect(status().isForbidden)

        val user = assertNotNull(userRepository.findByUsername(manager.username))
        assertEquals(null, walletJpaRepository.findByOwnerUserId(user.id.value))
    }

    @Test
    fun `invalid top-up does not create a wallet`() {
        val resident = register("invalid-wallet-resident", "RESIDENT")

        fund(resident.token, BigDecimal.ZERO)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))

        val user = assertNotNull(userRepository.findByUsername(resident.username))
        assertEquals(null, walletJpaRepository.findByOwnerUserId(user.id.value))
    }

    private fun fund(token: String, amount: BigDecimal) =
        mockMvc.perform(
            post("/api/v1/wallets/me/top-ups")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(FundWalletRequest(amount))),
        )

    private fun authorizedGet(path: String, token: String) =
        get(path).header(HttpHeaders.AUTHORIZATION, bearer(token))

    private fun register(usernamePrefix: String, role: String): RegisteredUser {
        val username = "$usernamePrefix-${UUID.randomUUID().toString().take(8)}"
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
        val token = objectMapper.readTree(result.response.contentAsString).get("token").asText()
        return RegisteredUser(username, token)
    }

    private fun bearer(token: String) = "Bearer $token"

    private data class RegisteredUser(
        val username: String,
        val token: String,
    )
}
