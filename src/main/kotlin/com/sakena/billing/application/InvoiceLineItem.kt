package com.sakena.billing.application

import com.sakena.billing.domain.model.ChargeItem
import java.math.BigDecimal

/** One cost line on a unit invoice, with that unit's allocated share. */
data class InvoiceLineItem(
    val item: ChargeItem,
    val shareAmount: BigDecimal,
)
