package com.sakena.payment.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sakena.IntegrationTest
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.PaymentReceiptStorage
import com.sakena.payment.infrastructure.persistence.PaymentJpaRepository
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.payment.infrastructure.web.dto.RejectPaymentRequest
import com.sakena.user.domain.UserRepository
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
import java.time.LocalDate
import java.util.UUID

@AutoConfigureMockMvc
class PaymentControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val paymentJpaRepository: PaymentJpaRepository,
    @Autowired private val userRepository: UserRepository,
) : IntegrationTest() {

    @MockkBean
    private lateinit var receiptStorage: PaymentReceiptStorage

    @Test
    fun `resident submission becomes history only after manager confirmation`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val residentToken = register("resident-$suffix", "RESIDENT")
        val otherResidentToken = register("other-$suffix", "RESIDENT")
        val managerToken = register("manager-$suffix", "MANAGER")
        val otherManagerToken = register("other-manager-$suffix", "MANAGER")
        createBuildingWithResident(managerToken, "resident-$suffix", suffix)
        val firstInvoiceId = issueInvoiceForResident(managerToken, residentToken, "1-$suffix")
        val secondInvoiceId = issueInvoiceForResident(managerToken, residentToken, "2-$suffix")
        val confirmedReference = "TX-CONFIRM-$suffix"
        val rejectedReference = "TX-REJECT-$suffix"
        val objectKey = "payment-receipts/$suffix/receipt.png"
        val signedUrl = "https://signed.example/$suffix"
        every { receiptStorage.store(any(), "image/png", any(), any()) } returns objectKey
        every { receiptStorage.presignedUrl(objectKey) } returns PaymentReceiptAccess(signedUrl, 900)

        val paymentId = submitPayment(
            token = residentToken,
            invoiceId = firstInvoiceId,
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

        mockMvc.perform(authorizedGet("/api/v1/payments/pending", otherManagerToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$paymentId')]").doesNotExist())

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", otherManagerToken))
            .andExpect(status().isForbidden)

        mockMvc.perform(
            patch("/api/v1/payments/$paymentId/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(otherManagerToken)),
        ).andExpect(status().isForbidden)

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", managerToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").value(signedUrl))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", residentToken))
            .andExpect(status().isOk)

        mockMvc.perform(authorizedGet("/api/v1/payments/$paymentId/receipt", otherResidentToken))
            .andExpect(status().isForbidden)

        submitPaymentRequest(residentToken, firstInvoiceId, confirmedReference, pngReceipt())
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
            invoiceId = secondInvoiceId,
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

    private fun createBuildingWithResident(
        managerToken: String,
        residentUsername: String,
        suffix: String,
    ) {
        val buildingId = managedBuildingId(managerToken)
        val apartmentBody = mapOf(
            "buildingId" to buildingId,
            "unitNumber" to "P-$suffix",
            "floorNumber" to 1,
            "areaSquareMeters" to BigDecimal("80.00"),
            "bedrooms" to 2,
        )
        val apartment = mockMvc.perform(
            post("/api/v1/apartments")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(apartmentBody)),
        )
            .andExpect(status().isCreated)
            .andReturn()
        val apartmentId = objectMapper.readTree(apartment.response.contentAsString).get("id").asText()
        val residentId = userRepository.findByUsername(residentUsername)?.id
            ?: error("Test resident was not persisted")
        val residencyBody = mapOf(
            "residentId" to residentId.value,
            "tenancy" to "TENANT",
        )
        mockMvc.perform(
            post("/api/v1/residencies/apartments/$apartmentId")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(residencyBody)),
        ).andExpect(status().isCreated)
    }

    private fun issueInvoiceForResident(
        managerToken: String,
        residentToken: String,
        suffix: String,
    ): String {
        val buildingId = managedBuildingId(managerToken)
        val periodBody = mapOf(
            "buildingId" to buildingId,
            "title" to "Charge $suffix",
            "type" to ChargePeriodType.MONTHLY.name,
            "startsOn" to LocalDate.of(2026, 6, 1).plusDays(suffix.hashCode().toLong().mod(20L)),
            "endsOn" to LocalDate.of(2026, 7, 1).plusDays(suffix.hashCode().toLong().mod(20L)),
        )
        val period = mockMvc.perform(
            post("/api/v1/charge-periods")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(periodBody)),
        )
            .andExpect(status().isCreated)
            .andReturn()
        val periodId = objectMapper.readTree(period.response.contentAsString).get("id").asText()

        val itemBody = mapOf(
            "title" to "Monthly charge",
            "amount" to BigDecimal("850000"),
            "kind" to ChargeItemKind.RECURRING_CHARGE.name,
            "allocation" to CostAllocation.EQUAL.name,
        )
        mockMvc.perform(
            post("/api/v1/charge-periods/$periodId/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(itemBody)),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/charge-periods/$periodId/issue")
                .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)),
        ).andExpect(status().isCreated)

        val invoices = mockMvc.perform(
            get("/api/v1/invoices/mine")
                .header(HttpHeaders.AUTHORIZATION, bearer(residentToken)),
        )
            .andExpect(status().isOk)
            .andReturn()
        val invoiceNode = objectMapper.readTree(invoices.response.contentAsString)
            .firstOrNull { it.get("periodId").asText() == periodId && it.get("remaining").decimalValue() > BigDecimal.ZERO }
            ?: error("Issued invoice for period $periodId was not found")
        return invoiceNode.get("id").asText()
    }

    private fun managedBuildingId(token: String): String {
        val result = mockMvc.perform(
            get("/api/v1/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)),
        )
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("managedBuildingId").asText()
    }

    private fun submitPayment(
        token: String,
        invoiceId: String,
        reference: String,
        receipt: MockMultipartFile?,
    ): String {
        val result = submitPaymentRequest(token, invoiceId, reference, receipt)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.transactionReference").value(reference))
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun submitPaymentRequest(
        token: String,
        invoiceId: String,
        reference: String,
        receipt: MockMultipartFile?,
    ): org.springframework.test.web.servlet.ResultActions {
        val request = RecordPaymentRequest(
            invoiceId = UUID.fromString(invoiceId),
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
