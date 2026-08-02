package com.sakena.payment.infrastructure.web.dto

import com.sakena.payment.application.command.PaymentReceiptUpload
import com.sakena.payment.application.command.SubmitPaymentCommand
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RecordPaymentRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,

    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotBlank(message = "transactionReference must not be blank")
    @field:Size(max = 100, message = "transactionReference must be at most 100 characters")
    val transactionReference: String,
) {
    fun toCommand(receipt: PaymentReceiptUpload?) = SubmitPaymentCommand(
        title = title,
        amount = amount,
        transactionReference = transactionReference,
        receipt = receipt,
    )
}

data class RejectPaymentRequest(
    @field:NotBlank(message = "reason must not be blank")
    @field:Size(max = 500, message = "reason must be at most 500 characters")
    val reason: String,
)

data class PaymentResponse(
    val id: UUID,
    val title: String,
    val amount: BigDecimal,
    val transactionReference: String,
    val hasReceipt: Boolean,
    val status: PaymentStatus,
    val paidAt: Instant,
    val reviewedBy: UUID?,
    val reviewedAt: Instant?,
    val rejectionReason: String?,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id.value,
            title = payment.title,
            amount = payment.amount,
            transactionReference = payment.transactionReference,
            hasReceipt = payment.receiptObjectKey != null,
            status = payment.status,
            paidAt = payment.paidAt,
            reviewedBy = payment.reviewedBy?.value,
            reviewedAt = payment.reviewedAt,
            rejectionReason = payment.rejectionReason,
        )
    }
}

data class PaymentReceiptResponse(
    val url: String,
    val expiresInSeconds: Int,
) {
    companion object {
        fun from(access: PaymentReceiptAccess) = PaymentReceiptResponse(
            url = access.url,
            expiresInSeconds = access.expiresInSeconds,
        )
    }
}
