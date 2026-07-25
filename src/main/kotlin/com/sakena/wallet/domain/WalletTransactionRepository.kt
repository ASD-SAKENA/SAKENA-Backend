package com.sakena.wallet.domain

import com.sakena.wallet.domain.model.WalletId
import com.sakena.wallet.domain.model.WalletTransaction

/**
 * Outbound port for the wallet ledger. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface WalletTransactionRepository {
    fun save(transaction: WalletTransaction): WalletTransaction

    /** A wallet's ledger, newest first. */
    fun findAllByWalletNewestFirst(walletId: WalletId): List<WalletTransaction>
}
