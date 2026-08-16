package com.sakena.payment.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sakena.payment.application.PaymentService
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.application.JwtTokenProvider
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.security.AuthRateLimitFilter
import com.sakena.user.infrastructure.security.JwtAuthenticationFilter
import com.sakena.user.infrastructure.security.SecurityConfig
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(PaymentController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    AuthRateLimitFilter::class,
)
class PaymentAuthorizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var paymentService: PaymentService

    @MockkBean
    private lateinit var profileService: ProfileService

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockkBean
    private lateinit var userRepository: UserRepository

    @Test
    @WithMockUser(username = "resident", roles = ["RESIDENT"])
    fun `resident may submit a payment claim`() {
        val resident = user("resident", Role.RESIDENT)
        val payment = pendingPayment(resident.id)
        every { profileService.getUserByUsername("resident") } returns resident
        every { paymentService.submit(any(), resident.id) } returns payment

        mockMvc.perform(
            multipart("/api/v1/payments")
                .file(jsonPart()),
        ).andExpect(status().isCreated)
    }

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager may not submit a resident payment claim`() {
        mockMvc.perform(
            multipart("/api/v1/payments")
                .file(jsonPart()),
        ).andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "resident", roles = ["RESIDENT"])
    fun `resident may not read the manager pending queue`() {
        mockMvc.perform(get("/api/v1/payments/pending"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager may read the pending queue`() {
        val manager = user("manager", Role.MANAGER)
        every { profileService.getUserByUsername("manager") } returns manager
        every { paymentService.getPending(manager.id) } returns emptyList()

        mockMvc.perform(get("/api/v1/payments/pending"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "manager", roles = ["MANAGER"])
    fun `manager may request a payment receipt link`() {
        val manager = user("manager", Role.MANAGER)
        val payment = pendingPayment(UserId.generate())
        every { profileService.getUserByUsername("manager") } returns manager
        every { paymentService.getReceipt(payment.id, manager.id) } returns
            com.sakena.payment.domain.PaymentReceiptAccess("https://signed.example/receipt", 900)

        mockMvc.perform(get("/api/v1/payments/${payment.id}/receipt"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "staff", roles = ["STAFF"])
    fun `staff may not request a payment receipt link`() {
        val payment = pendingPayment(UserId.generate())

        mockMvc.perform(get("/api/v1/payments/${payment.id}/receipt"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "resident", roles = ["RESIDENT"])
    fun `resident may not confirm a payment`() {
        mockMvc.perform(patch("/api/v1/payments/${pendingPayment(UserId.generate()).id}/confirm"))
            .andExpect(status().isForbidden)
    }

    private fun request() = RecordPaymentRequest(
        title = "Monthly charge",
        amount = BigDecimal("500000"),
        transactionReference = "TX-AUTH-123",
    )

    private fun jsonPart() = MockMultipartFile(
        "payment",
        "payment.json",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(request()),
    )

    private fun pendingPayment(payerId: UserId): Payment = Payment.submit(
        buildingId = BuildingId.new(),
        payerId = payerId,
        title = "Monthly charge",
        amount = BigDecimal("500000"),
        transactionReference = "TX-AUTH-123",
        receiptObjectKey = null,
    )

    private fun user(username: String, role: Role): User {
        val now = Instant.now()
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
