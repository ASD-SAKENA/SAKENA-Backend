package com.sakena.payment.domain

import com.sakena.payment.domain.model.Payment
import com.sakena.user.domain.UserId

/**
 * Outbound port for persisting payments. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface PaymentRepository {
    fun save(payment: Payment): Payment

    /** A resident's payment history, newest first. */
    fun findAllByPayerNewestFirst(payerId: UserId): List<Payment>
}
