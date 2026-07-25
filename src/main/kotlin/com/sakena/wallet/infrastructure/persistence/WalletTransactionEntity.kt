package com.sakena.wallet.infrastructure.persistence

import com.sakena.wallet.domain.model.TransactionCategory
import com.sakena.wallet.domain.model.TransactionDirection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** JPA persistence model for wallet ledger lines. */
@Entity
@Table(name = "wallet_transactions")
class WalletTransactionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "wallet_id", nullable = false, updatable = false)
    var walletId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    var direction: TransactionDirection,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: TransactionCategory,

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    var amount: BigDecimal,

    @Column(name = "description", nullable = false, length = 300)
    var description: String,

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    var balanceAfter: BigDecimal,

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant,
)
