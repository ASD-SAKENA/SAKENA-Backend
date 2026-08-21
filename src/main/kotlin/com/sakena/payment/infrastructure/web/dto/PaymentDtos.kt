package com.sakena.payment.infrastructure.web.dto

import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.payment.application.PaymentDetails
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
    @field:NotNull(message = "invoiceId must not be null")
    val invoiceId: UUID,

    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotBlank(message = "transactionReference must not be blank")
    @field:Size(max = 100, message = "transactionReference must be at most 100 characters")
    val transactionReference: String,
) {
    fun toCommand(receipt: PaymentReceiptUpload?) = SubmitPaymentCommand(
        invoiceId = UnitInvoiceId(invoiceId),
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
    val invoiceId: UUID?,
    val periodId: UUID?,
    val periodTitle: String?,
    val unitNumber: String?,
    val payerUsername: String?,
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
        fun from(payment: Payment, periodTitle: String? = null) = PaymentResponse(
            id = payment.id.value,
            invoiceId = payment.invoiceId?.value,
            periodId = null,
            periodTitle = periodTitle,
            unitNumber = null,
            payerUsername = null,
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

        fun from(details: PaymentDetails) = PaymentResponse(
            id = details.payment.id.value,
            invoiceId = details.payment.invoiceId?.value,
            periodId = details.periodId?.value,
            periodTitle = details.periodTitle,
            unitNumber = details.unitNumber,
            payerUsername = details.payerUsername,
            title = details.payment.title,
            amount = details.payment.amount,
            transactionReference = details.payment.transactionReference,
            hasReceipt = details.payment.receiptObjectKey != null,
            status = details.payment.status,
            paidAt = details.payment.paidAt,
            reviewedBy = details.payment.reviewedBy?.value,
            reviewedAt = details.payment.reviewedAt,
            rejectionReason = details.payment.rejectionReason,
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
