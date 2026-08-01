package com.sakena.payment.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.payment.application.PaymentService
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.shared.web.GlobalExceptionHandler
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.math.BigDecimal
import java.time.Instant

class PaymentControllerTest {

    private val paymentService = mockk<PaymentService>()
    private val profileService = mockk<ProfileService>()
    private val objectMapper = jacksonObjectMapper()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(PaymentController(paymentService, profileService))
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator())
        .build()

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `record maps payment evidence to a pending claim`() {
        val residentId = UserId.generate()
        val payment = Payment.submit(
            residentId,
            "Monthly charge",
            BigDecimal("500000"),
            "TX-123",
            null,
        )
        every {
            paymentService.record(
                match {
                    it.title == "Monthly charge" &&
                        it.amount == BigDecimal("500000") &&
                        it.transactionReference == "TX-123"
                },
                residentId,
            )
        } returns payment

        val body = objectMapper.writeValueAsString(
            RecordPaymentRequest(
                title = "Monthly charge",
                amount = BigDecimal("500000"),
                transactionReference = "TX-123",
            ),
        )

        mockMvc.perform(
            post("/api/v1/payments/${residentId.value}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Monthly charge"))
            .andExpect(jsonPath("$.amount").value(500000))
            .andExpect(jsonPath("$.transactionReference").value("TX-123"))
            .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name))

        verify(exactly = 1) { paymentService.record(any(), residentId) }
        verify(exactly = 0) { profileService.getUserByUsername(any()) }
    }

    @Test
    fun `resident reads only the payment history associated with their identity`() {
        val resident = user("resident", Role.RESIDENT)
        val payment = Payment.submit(
            resident.id,
            "Previous charge",
            BigDecimal("250000"),
            "TX-CONFIRMED",
            null,
        ).also { it.confirm(UserId.generate()) }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(resident.username, null)
        every { profileService.getUserByUsername(resident.username) } returns resident
        every { paymentService.getHistory(resident.id) } returns listOf(payment)

        mockMvc.perform(get("/api/v1/payments"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("Previous charge"))
            .andExpect(jsonPath("$[0].amount").value(250000))

        verify(exactly = 1) { paymentService.getHistory(resident.id) }
    }

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

    private fun validator(): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().also { it.afterPropertiesSet() }
}
