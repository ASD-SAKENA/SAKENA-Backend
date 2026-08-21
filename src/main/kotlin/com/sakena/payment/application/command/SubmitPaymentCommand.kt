package com.sakena.payment.application.command

import com.sakena.billing.domain.model.UnitInvoiceId
import java.io.InputStream
import java.math.BigDecimal

data class PaymentReceiptUpload(
    val contentType: String,
    val sizeBytes: Long,
    val content: InputStream,
)

data class SubmitPaymentCommand(
    val invoiceId: UnitInvoiceId,
    val amount: BigDecimal,
    val transactionReference: String,
    val receipt: PaymentReceiptUpload?,
)
