package com.sakena.payment.infrastructure.web.dto

import com.sakena.payment.application.command.RecordPaymentCommand
import com.sakena.payment.domain.model.Payment
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RecordPaymentRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,

    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal,
) {
    fun toCommand() = RecordPaymentCommand(title = title, amount = amount)
}

data class PaymentResponse(
    val id: UUID,
    val title: String,
    val amount: BigDecimal,
    val paidAt: Instant,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id.value,
            title = payment.title,
            amount = payment.amount,
            paidAt = payment.paidAt,
        )
    }
}
