package com.sakena.payment.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.math.BigDecimal
import java.time.Instant

enum class PaymentStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
}

/**
 * A resident's payment claim. It enters the system as pending and becomes part
 * of the permanent payment history only after a manager confirms it.
 */
class Payment private constructor(
    val id: PaymentId,
    val payerId: UserId,
    val title: String,
    val amount: BigDecimal,
    val transactionReference: String,
    val receiptObjectKey: String?,
    status: PaymentStatus,
    val paidAt: Instant,
    reviewedBy: UserId?,
    reviewedAt: Instant?,
    rejectionReason: String?,
) {
    var status: PaymentStatus = status
        private set

    var reviewedBy: UserId? = reviewedBy
        private set

    var reviewedAt: Instant? = reviewedAt
        private set

    var rejectionReason: String? = rejectionReason
        private set

    fun confirm(managerId: UserId) {
        requirePending()
        status = PaymentStatus.CONFIRMED
        reviewedBy = managerId
        reviewedAt = Instant.now()
    }

    fun reject(managerId: UserId, reason: String) {
        requirePending()
        val validatedReason = validateRejectionReason(reason)
        status = PaymentStatus.REJECTED
        reviewedBy = managerId
        reviewedAt = Instant.now()
        rejectionReason = validatedReason
    }

    private fun requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw DomainConflictException("Only pending payments can be reviewed")
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_TRANSACTION_REFERENCE_LENGTH = 100
        const val MAX_RECEIPT_OBJECT_KEY_LENGTH = 500
        const val MAX_REJECTION_REASON_LENGTH = 500

        fun submit(
            payerId: UserId,
            title: String,
            amount: BigDecimal,
            transactionReference: String,
            receiptObjectKey: String?,
        ): Payment =
            Payment(
                id = PaymentId.new(),
                payerId = payerId,
                title = validateTitle(title),
                amount = validateAmount(amount),
                transactionReference = validateTransactionReference(transactionReference),
                receiptObjectKey = validateReceiptObjectKey(receiptObjectKey),
                status = PaymentStatus.PENDING,
                paidAt = Instant.now(),
                reviewedBy = null,
                reviewedAt = null,
                rejectionReason = null,
            )

        /** Rebuilds an aggregate from already-persisted state. */
        fun reconstitute(
            id: PaymentId,
            payerId: UserId,
            title: String,
            amount: BigDecimal,
            transactionReference: String,
            receiptObjectKey: String?,
            status: PaymentStatus,
            paidAt: Instant,
            reviewedBy: UserId?,
            reviewedAt: Instant?,
            rejectionReason: String?,
        ): Payment = Payment(
            id = id,
            payerId = payerId,
            title = title,
            amount = amount,
            transactionReference = transactionReference,
            receiptObjectKey = receiptObjectKey,
            status = status,
            paidAt = paidAt,
            reviewedBy = reviewedBy,
            reviewedAt = reviewedAt,
            rejectionReason = rejectionReason,
        )

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

        private fun validateTransactionReference(reference: String): String {
            val trimmed = reference.trim()
            if (trimmed.isEmpty()) {
                throw DomainValidationException("Transaction reference must not be blank")
            }
            if (trimmed.length > MAX_TRANSACTION_REFERENCE_LENGTH) {
                throw DomainValidationException(
                    "Transaction reference must be at most $MAX_TRANSACTION_REFERENCE_LENGTH characters",
                )
            }
            return trimmed
        }

        private fun validateReceiptObjectKey(receiptObjectKey: String?): String? {
            val trimmed = receiptObjectKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.length > MAX_RECEIPT_OBJECT_KEY_LENGTH) {
                throw DomainValidationException(
                    "Receipt object key must be at most $MAX_RECEIPT_OBJECT_KEY_LENGTH characters",
                )
            }
            return trimmed
        }

        private fun validateRejectionReason(reason: String): String {
            val trimmed = reason.trim()
            if (trimmed.isEmpty()) {
                throw DomainValidationException("Rejection reason must not be blank")
            }
            if (trimmed.length > MAX_REJECTION_REASON_LENGTH) {
                throw DomainValidationException(
                    "Rejection reason must be at most $MAX_REJECTION_REASON_LENGTH characters",
                )
            }
            return trimmed
        }
    }
}
