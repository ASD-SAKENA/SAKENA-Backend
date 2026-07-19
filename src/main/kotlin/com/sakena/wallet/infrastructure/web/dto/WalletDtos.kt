package com.sakena.wallet.infrastructure.web.dto

import com.sakena.wallet.domain.model.Wallet
import java.math.BigDecimal

data class WalletResponse(
    val balance: BigDecimal,
) {
    companion object {
        fun from(wallet: Wallet) = WalletResponse(balance = wallet.balance)
    }
}
