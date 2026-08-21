package com.sakena.wallet.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Value object identifying a [WalletTransaction]. */
@JvmInline
value class WalletTransactionId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): WalletTransactionId = WalletTransactionId(UUID.randomUUID())
    }
}

/** Which way money moved on the wallet. */
enum class TransactionDirection {
    /** Money in — resident charges collected, top-ups. */
    CREDIT,

    /** Money out — worker wages, running costs. */
    DEBIT,
}

/** Why the money moved; drives grouping in wallet ledger views. */
enum class TransactionCategory {
    WALLET_FUNDING,
    CHARGE_COLLECTION,
    WAGE_SETTLEMENT,
    OPERATING_EXPENSE,
    ADJUSTMENT,

    /** Paying for — or being refunded for — a shared-facility reservation. */
    FACILITY_BOOKING,
}

/**
 * WalletTransaction aggregate — one immutable line in a wallet's ledger. Every
 * balance change is written as a transaction so the manager can always see
 * where the building's money came from and went.
 */
class WalletTransaction private constructor(
    val id: WalletTransactionId,
    val walletId: WalletId,
    val direction: TransactionDirection,
    val category: TransactionCategory,
    val amount: BigDecimal,
    val description: String,
    /** Balance right after this transaction, so the ledger reads like a statement. */
    val balanceAfter: BigDecimal,
    val occurredAt: Instant,
) {

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 300

        fun record(
            walletId: WalletId,
            direction: TransactionDirection,
            category: TransactionCategory,
            amount: BigDecimal,
            description: String,
            balanceAfter: BigDecimal,
        ): WalletTransaction {
            if (amount <= BigDecimal.ZERO) {
                throw DomainValidationException("Transaction amount must be greater than zero")
            }
            return WalletTransaction(
                id = WalletTransactionId.new(),
                walletId = walletId,
                direction = direction,
                category = category,
                amount = amount,
                description = validateDescription(description),
                balanceAfter = balanceAfter,
                occurredAt = Instant.now(),
            )
        }

        /** Rebuilds from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: WalletTransactionId,
            walletId: WalletId,
            direction: TransactionDirection,
            category: TransactionCategory,
            amount: BigDecimal,
            description: String,
            balanceAfter: BigDecimal,
            occurredAt: Instant,
        ): WalletTransaction = WalletTransaction(
            id, walletId, direction, category, amount, description, balanceAfter, occurredAt,
        )

        private fun validateDescription(description: String): String {
            val trimmed = description.trim()
            if (trimmed.isEmpty()) {
                throw DomainValidationException("Transaction description must not be blank")
            }
            if (trimmed.length > MAX_DESCRIPTION_LENGTH) {
                throw DomainValidationException(
                    "Transaction description must be at most $MAX_DESCRIPTION_LENGTH characters",
                )
            }
            return trimmed
        }
    }
}
