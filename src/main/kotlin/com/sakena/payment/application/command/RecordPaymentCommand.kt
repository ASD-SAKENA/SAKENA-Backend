package com.sakena.payment.application.command

import java.math.BigDecimal

data class RecordPaymentCommand(
    val title: String,
    val amount: BigDecimal,
)
