package com.sakena.payment.application.command

import java.math.BigDecimal

data class SubmitPaymentCommand(
    val title: String,
    val amount: BigDecimal,
    val transactionReference: String,
    val receiptObjectKey: String?,
)
