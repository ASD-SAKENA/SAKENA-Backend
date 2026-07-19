package com.sakena.payment.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.math.BigDecimal
import java.time.Instant

/**
 * Payment aggregate root — an amount a resident has paid (e.g. the monthly
 * charge). Payments are immutable records: once made they are never edited,
 * so the aggregate exposes no mutation behaviour.
 */
class Payment private constructor(
    val id: PaymentId,
    val payerId: UserId,
    val title: String,
    val amount: BigDecimal,
    val paidAt: Instant,
) {

    companion object {
        const val MAX_TITLE_LENGTH = 200

        fun create(payerId: UserId, title: String, amount: BigDecimal): Payment =
            Payment(
                id = PaymentId.new(),
                payerId = payerId,
                title = validateTitle(title),
                amount = validateAmount(amount),
                paidAt = Instant.now(),
            )

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: PaymentId,
            payerId: UserId,
            title: String,
            amount: BigDecimal,
            paidAt: Instant,
        ): Payment = Payment(id, payerId, title, amount, paidAt)

        private fun validateTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Payment title must not be blank")
            if (trimmed.length > MAX_TITLE_LENGTH) {
                throw DomainValidationException("Payment title must be at most $MAX_TITLE_LENGTH characters")
            }
            return trimmed
        }

        private fun validateAmount(amount: BigDecimal): BigDecimal {
            if (amount <= BigDecimal.ZERO) {
                throw DomainValidationException("Payment amount must be greater than zero")
            }
            return amount
        }
    }
}
