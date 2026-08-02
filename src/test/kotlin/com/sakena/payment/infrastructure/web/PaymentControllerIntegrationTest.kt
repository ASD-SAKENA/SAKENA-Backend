package com.sakena.payment.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sakena.IntegrationTest
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.PaymentReceiptStorage
import com.sakena.payment.infrastructure.persistence.PaymentJpaRepository
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.payment.infrastructure.web.dto.RejectPaymentRequest
import com.sakena.user.infrastructure.web.RegisterRequest
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@AutoConfigureMockMvc
class PaymentControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val paymentJpaRepository: PaymentJpaRepository,
) : IntegrationTest() {

    @MockkBean
    private lateinit var receiptStorage: PaymentReceiptStorage

    @Test
    fun `resident submission becomes history only after manager confirmation`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val residentToken = register("resident-$suffix", "RESIDENT")
        val otherResidentToken = register("other-$suffix", "RESIDENT")
        val managerToken = register("manager-$suffix", "MANAGER")
        val confirmedReference = "TX-CONFIRM-$suffix"
        val rejectedReference = "TX-REJECT-$suffix"
        val objectKey = "payment-receipts/$suffix/receipt.png"
        val signedUrl = "https://signed.example/$suffix"
        every { receiptStorage.store(any(), "image/png", any(), any()) } returns objectKey
        every { receiptStorage.presignedUrl(objectKey) } returns PaymentReceiptAccess(signedUrl, 900)

        val paymentId = submitPayment(
            token = residentToken,
            reference = confirmedReference,
            receipt = pngReceipt(),
        )

        mockMvc.perform(authorizedGet("/api/v1/payments", residentToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)

        mockMvc.perform(authorizedGet("/api/v1/payments/submissions", residentToken))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$[?(@.id == '$paymentId' && @.status == 'PENDING')]").exists(),
            )
            .andExpect(
                jsonPath("$[?(@.id == '$paymentId' && @.hasReceipt == true)]").exists(),
            )
            .andExpect(jsonPath("$[0].receiptObjectKey").doesNotExist())

        val persistedPending = paymentJpaRepository.findById(UUID.fromString(paymentId)).orElseThrow()
        kotlin.test.assertEquals(objectKey, persistedPending.receiptObjectKey)

        mockMvc.perform(authorizedGet("/api/v1/payments/pending", managerToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$paymentId')]").exists())

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", managerToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").value(signedUrl))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", residentToken))
            .andExpect(status().isOk)

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", otherResidentToken))
            .andExpect(status().isForbidden)

        submitPaymentRequest(residentToken, confirmedReference, pngReceipt())
            .andExpect(status().isConflict)
        verify(exactly = 1) { receiptStorage.store(any(), any(), any(), any()) }

        mockMvc.perform(
            patch("/api/v1/payments/$paymentId/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))

        mockMvc.perform(authorizedGet("/api/v1/payments", residentToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$paymentId')]").exists())

        val rejectedPaymentId = submitPayment(
            token = residentToken,
            reference = rejectedReference,
            receipt = null,
        )
        val rejectionBody = objectMapper.writeValueAsString(
            RejectPaymentRequest("Reference could not be verified"),
        )
        mockMvc.perform(
            patch("/api/v1/payments/$rejectedPaymentId/reject")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rejectionBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Reference could not be verified"))

        mockMvc.perform(authorizedGet("/api/v1/payments", residentToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$rejectedPaymentId')]").doesNotExist())

        mockMvc.perform(authorizedGet("/api/v1/payments/submissions", residentToken))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$[?(@.id == '$rejectedPaymentId' && @.status == 'REJECTED')]").exists(),
            )

        verify(exactly = 2) { receiptStorage.presignedUrl(objectKey) }
    }

    private fun register(username: String, role: String): String {
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
        return objectMapper.readTree(result.response.contentAsString).get("token").asText()
    }

    private fun submitPayment(token: String, reference: String, receipt: MockMultipartFile?): String {
        val result = submitPaymentRequest(token, reference, receipt)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.transactionReference").value(reference))
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun submitPaymentRequest(
        token: String,
        reference: String,
        receipt: MockMultipartFile?,
    ): org.springframework.test.web.servlet.ResultActions {
        val request = RecordPaymentRequest(
            title = "Monthly charge",
            amount = BigDecimal("850000"),
            transactionReference = reference,
        )
        val builder = multipart("/api/v1/payments")
        builder.file(
            MockMultipartFile(
                "payment",
                "payment.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request),
            ),
        )
        builder.header(HttpHeaders.AUTHORIZATION, bearer(token))
        if (receipt != null) builder.file(receipt)
        return mockMvc.perform(builder)
    }

    private fun pngReceipt() = MockMultipartFile(
        "receipt",
        "receipt.png",
        MediaType.IMAGE_PNG_VALUE,
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
    )

    private fun authorizedGet(path: String, token: String) =
        get(path).header(HttpHeaders.AUTHORIZATION, bearer(token))

    private fun bearer(token: String) = "Bearer $token"
}
