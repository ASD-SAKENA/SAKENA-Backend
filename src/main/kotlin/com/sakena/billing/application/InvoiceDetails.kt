package com.sakena.billing.application

import com.sakena.billing.domain.model.UnitInvoice
import java.time.LocalDate

/** Invoice row enriched for manager collection screens. */
data class InvoiceDetails(
    val invoice: UnitInvoice,
    val periodTitle: String,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val unitNumber: String?,
    val residentUsername: String?,
)
