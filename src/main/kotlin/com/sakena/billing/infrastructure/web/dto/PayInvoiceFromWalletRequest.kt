package com.sakena.billing.infrastructure.web.dto

import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

/** Optional amount for wallet settlement — omit to pay the full remaining balance. */
data class PayInvoiceFromWalletRequest(
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal? = null,
)
