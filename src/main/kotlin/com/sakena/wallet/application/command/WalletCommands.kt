package com.sakena.wallet.application.command

import com.sakena.wallet.domain.model.TransactionCategory
import com.sakena.wallet.domain.model.TransactionDirection
import java.math.BigDecimal

data class RecordBuildingTransactionCommand(
    val direction: TransactionDirection,
    val category: TransactionCategory,
    val amount: BigDecimal,
    val description: String,
)
