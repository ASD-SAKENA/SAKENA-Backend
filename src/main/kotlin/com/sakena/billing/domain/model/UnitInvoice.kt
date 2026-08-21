package com.sakena.billing.domain.model

import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import java.math.BigDecimal
import java.time.Instant

/** Settlement state of a [UnitInvoice], derived from the amount paid so far. */
enum class InvoiceStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
}

/**
 * UnitInvoice aggregate root — what a single unit owes for one charge period.
 * The amount is frozen at issue time; payments accumulate against it and the
 * status is always derived, never set directly.
 */
class UnitInvoice private constructor(
    val id: UnitInvoiceId,
    val periodId: ChargePeriodId,
    val apartmentId: ApartmentId,
    val amount: BigDecimal,
    paidAmount: BigDecimal,
    val issuedAt: Instant,
    updatedAt: Instant,
) {
    var paidAmount: BigDecimal = paidAmount
        private set

    var updatedAt: Instant = updatedAt
        private set

    val status: InvoiceStatus
        get() = when {
            paidAmount >= amount -> InvoiceStatus.PAID
            paidAmount > BigDecimal.ZERO -> InvoiceStatus.PARTIALLY_PAID
            else -> InvoiceStatus.UNPAID
        }

    val remaining: BigDecimal
        get() = (amount - paidAmount).max(BigDecimal.ZERO)

    fun registerPayment(payment: BigDecimal) {
        if (payment <= BigDecimal.ZERO) {
            throw DomainValidationException("Payment amount must be greater than zero")
        }
        // Invoices issued before charges were split in whole Toman can still
        // carry a fraction, so a payment may too — but never more precision
        // than the currency column holds.
        if (payment.stripTrailingZeros().scale() > MAX_PAYMENT_SCALE) {
            throw DomainValidationException(
                "Payment amount must have at most $MAX_PAYMENT_SCALE decimal places",
            )
        }
        if (status == InvoiceStatus.PAID) {
            throw DomainConflictException("Invoice is already fully paid")
        }
        if (payment > remaining) {
            throw DomainConflictException("Payment exceeds the outstanding amount of this invoice")
        }
        paidAmount += payment
        updatedAt = Instant.now()
    }

    companion object {
        /** Matches the NUMERIC(18, 2) the amount is stored in. */
        const val MAX_PAYMENT_SCALE = 2

        fun issue(
            periodId: ChargePeriodId,
            apartmentId: ApartmentId,
            amount: BigDecimal,
        ): UnitInvoice {
            if (amount <= BigDecimal.ZERO) {
                throw DomainValidationException("Invoice amount must be greater than zero")
            }
            val now = Instant.now()
            return UnitInvoice(
                id = UnitInvoiceId.new(),
                periodId = periodId,
                apartmentId = apartmentId,
                amount = amount,
                paidAmount = BigDecimal.ZERO,
                issuedAt = now,
                updatedAt = now,
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: UnitInvoiceId,
            periodId: ChargePeriodId,
            apartmentId: ApartmentId,
            amount: BigDecimal,
            paidAmount: BigDecimal,
            issuedAt: Instant,
            updatedAt: Instant,
        ): UnitInvoice =
            UnitInvoice(id, periodId, apartmentId, amount, paidAmount, issuedAt, updatedAt)
    }
}
