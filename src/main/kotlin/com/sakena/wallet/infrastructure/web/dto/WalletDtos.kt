package com.sakena.wallet.infrastructure.web.dto

import com.sakena.wallet.application.command.FundWalletCommand
import com.sakena.wallet.application.command.RecordBuildingTransactionCommand
import com.sakena.wallet.domain.model.TransactionCategory
import com.sakena.wallet.domain.model.TransactionDirection
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.domain.model.WalletTransaction
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class WalletResponse(
    val balance: BigDecimal,
) {
    companion object {
        fun from(wallet: Wallet) = WalletResponse(balance = wallet.balance)
    }
}

data class FundWalletRequest(
    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @field:Digits(
        integer = 16,
        fraction = 2,
        message = "amount must have at most 16 integer digits and 2 decimal places",
    )
    val amount: BigDecimal,
) {
    fun toCommand() = FundWalletCommand(amount = amount)
}

data class RecordBuildingTransactionRequest(
    @field:NotNull(message = "direction must not be null")
    val direction: TransactionDirection,

    @field:NotNull(message = "category must not be null")
    val category: TransactionCategory,

    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotBlank(message = "description must not be blank")
    @field:Size(max = 300, message = "description must be at most 300 characters")
    val description: String,
) {
    fun toCommand() = RecordBuildingTransactionCommand(
        direction = direction,
        category = category,
        amount = amount,
        description = description,
    )
}

data class WalletTransactionResponse(
    val id: UUID,
    val direction: TransactionDirection,
    val category: TransactionCategory,
    val amount: BigDecimal,
    val description: String,
    val balanceAfter: BigDecimal,
    val occurredAt: Instant,
) {
    companion object {
        fun from(transaction: WalletTransaction) = WalletTransactionResponse(
            id = transaction.id.value,
            direction = transaction.direction,
            category = transaction.category,
            amount = transaction.amount,
            description = transaction.description,
            balanceAfter = transaction.balanceAfter,
            occurredAt = transaction.occurredAt,
        )
    }
}
