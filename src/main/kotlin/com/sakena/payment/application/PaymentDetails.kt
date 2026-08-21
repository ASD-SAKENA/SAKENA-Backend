package com.sakena.payment.application

import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus

/**
 * Payment row enriched for manager screens: which charge period it belongs to,
 * which unit paid, and who submitted the claim.
 */
data class PaymentDetails(
    val payment: Payment,
    val periodId: ChargePeriodId?,
    val periodTitle: String?,
    val unitNumber: String?,
    val payerUsername: String?,
)

data class BuildingPaymentQuery(
    val status: PaymentStatus? = null,
    val periodId: ChargePeriodId? = null,
)
