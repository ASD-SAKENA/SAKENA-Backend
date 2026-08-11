package com.sakena.payment.domain

import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentId
import com.sakena.user.domain.UserId

/**
 * Outbound port for persisting payments. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findById(id: PaymentId): Payment?

    fun existsByTransactionReference(transactionReference: String): Boolean

    /** A resident's confirmed permanent payment history, newest first. */
    fun findAllByPayerNewestFirst(payerId: UserId): List<Payment>

    /** Every claim submitted by a resident, regardless of review status. */
    fun findAllSubmissionsByPayerNewestFirst(payerId: UserId): List<Payment>

    /** The global manager review queue under the current authorization model. */
    fun findAllPendingNewestFirst(): List<Payment>
}
