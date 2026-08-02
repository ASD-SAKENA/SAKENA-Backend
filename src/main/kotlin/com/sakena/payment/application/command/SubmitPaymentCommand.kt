package com.sakena.payment.application.command

import java.io.InputStream
import java.math.BigDecimal

data class PaymentReceiptUpload(
    val contentType: String,
    val sizeBytes: Long,
    val content: InputStream,
)

data class SubmitPaymentCommand(
    val title: String,
    val amount: BigDecimal,
    val transactionReference: String,
    val receipt: PaymentReceiptUpload?,
)
