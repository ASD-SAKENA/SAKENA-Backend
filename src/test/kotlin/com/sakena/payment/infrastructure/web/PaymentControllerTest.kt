package com.sakena.payment.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.payment.application.PaymentService
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.payment.infrastructure.web.dto.RejectPaymentRequest
import com.sakena.property.domain.model.BuildingId
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
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
    fun `resident submits evidence using their authenticated identity`() {
        val resident = authenticate(Role.RESIDENT)
        val payment = pendingPayment(
            resident.id,
            "TX-123",
            "payment-receipts/${resident.id}/receipt.png",
        )
        every { paymentService.submit(any(), resident.id) } returns payment
        val paymentPart = jsonPart(
            RecordPaymentRequest(
                title = "Monthly charge",
                amount = BigDecimal("500000"),
                transactionReference = "TX-123",
            ),
        )
        val receiptPart = MockMultipartFile(
            "receipt",
            "receipt.png",
            MediaType.IMAGE_PNG_VALUE,
            "receipt image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/payments")
                .file(paymentPart)
                .file(receiptPart),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.transactionReference").value("TX-123"))
            .andExpect(jsonPath("$.hasReceipt").value(true))
            .andExpect(jsonPath("$.receiptObjectKey").doesNotExist())
            .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name))

        verify(exactly = 1) {
            paymentService.submit(
                match {
                    it.transactionReference == "TX-123" &&
                        it.receipt?.contentType == MediaType.IMAGE_PNG_VALUE
                },
                resident.id,
            )
        }
    }

    @Test
    fun `resident reads all of their submissions`() {
        val resident = authenticate(Role.RESIDENT)
        val payment = pendingPayment(resident.id, "TX-PENDING")
        every { paymentService.getSubmissions(resident.id) } returns listOf(payment)

        mockMvc.perform(get("/api/v1/payments/submissions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value(PaymentStatus.PENDING.name))
    }

    @Test
    fun `resident reads only their confirmed history`() {
        val resident = authenticate(Role.RESIDENT)
        val payment = pendingPayment(resident.id, "TX-CONFIRMED").also {
            it.confirm(UserId.generate())
        }
        every { paymentService.getHistory(resident.id) } returns listOf(payment)

        mockMvc.perform(get("/api/v1/payments"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value(PaymentStatus.CONFIRMED.name))
    }

    @Test
    fun `manager reads the pending review queue`() {
        val manager = authenticate(Role.MANAGER)
        val payment = pendingPayment(UserId.generate(), "TX-PENDING")
        every { paymentService.getPending(manager.id) } returns listOf(payment)

        mockMvc.perform(get("/api/v1/payments/pending"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].transactionReference").value("TX-PENDING"))
    }

    @Test
    fun `authorized user requests a temporary receipt link`() {
        val manager = authenticate(Role.MANAGER)
        val payment = pendingPayment(UserId.generate(), "TX-RECEIPT")
        every {
            paymentService.getReceipt(payment.id, manager.id)
        } returns PaymentReceiptAccess("https://signed.example/receipt", 900)

        mockMvc.perform(get("/api/v1/payments/${payment.id}/receipt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").value("https://signed.example/receipt"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
    }

    @Test
    fun `manager confirms a pending payment`() {
        val manager = authenticate(Role.MANAGER)
        val payment = pendingPayment(UserId.generate(), "TX-CONFIRM").also {
            it.confirm(manager.id)
        }
        every { paymentService.confirm(payment.id, manager.id) } returns payment

        mockMvc.perform(patch("/api/v1/payments/${payment.id}/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(PaymentStatus.CONFIRMED.name))
            .andExpect(jsonPath("$.reviewedBy").value(manager.id.value.toString()))
    }

    @Test
    fun `manager rejects a pending payment with a reason`() {
        val manager = authenticate(Role.MANAGER)
        val payment = pendingPayment(UserId.generate(), "TX-REJECT").also {
            it.reject(manager.id, "Reference not found")
        }
        every {
            paymentService.reject(payment.id, manager.id, "Reference not found")
        } returns payment
        val body = objectMapper.writeValueAsString(RejectPaymentRequest("Reference not found"))

        mockMvc.perform(
            patch("/api/v1/payments/${payment.id}/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.name))
            .andExpect(jsonPath("$.rejectionReason").value("Reference not found"))
    }

    @Test
    fun `blank rejection reason returns validation error`() {
        val manager = authenticate(Role.MANAGER)
        val body = objectMapper.writeValueAsString(RejectPaymentRequest(""))

        mockMvc.perform(
            patch("/api/v1/payments/${com.sakena.payment.domain.model.PaymentId.new()}/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))

        verify(exactly = 0) { paymentService.reject(any(), manager.id, any()) }
    }

    private fun pendingPayment(
        payerId: UserId,
        reference: String,
        receiptObjectKey: String? = null,
    ): Payment =
        Payment.submit(
            payerId = payerId,
            title = "Monthly charge",
            amount = BigDecimal("500000"),
            transactionReference = reference,
            receiptObjectKey = receiptObjectKey,
        )

    private fun jsonPart(request: RecordPaymentRequest) = MockMultipartFile(
        "payment",
        "payment.json",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(request),
    )

    private fun authenticate(role: Role): User {
        val user = user(role)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user.username, null)
        every { profileService.getUserByUsername(user.username) } returns user
        return user
    }

    private fun user(role: Role): User {
        val id = UserId.generate()
        val now = Instant.now()
        return User.reconstitute(
            id = id,
            username = "user-${id.value}",
            email = "${id.value}@example.com",
            passwordHash = "hash",
            role = role,
            createdAt = now,
            updatedAt = now,
            active = true,
            managedBuildingId = if (role == Role.MANAGER) BuildingId.new() else null,
        )
    }

    private fun validator(): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().also { it.afterPropertiesSet() }
}
