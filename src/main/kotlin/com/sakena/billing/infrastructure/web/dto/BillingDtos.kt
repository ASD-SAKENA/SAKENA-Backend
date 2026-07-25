package com.sakena.billing.infrastructure.web.dto

import com.sakena.billing.application.command.AddChargeItemCommand
import com.sakena.billing.application.command.CreateChargePeriodCommand
import com.sakena.billing.application.command.RegisterInvoicePaymentCommand
import com.sakena.billing.application.command.UpdateChargePeriodCommand
import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.billing.domain.model.InvoiceStatus
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.property.domain.model.BuildingId
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateChargePeriodRequest(
    @field:NotNull(message = "buildingId must not be null")
    val buildingId: UUID,

    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 150, message = "title must be at most 150 characters")
    val title: String,

    @field:NotNull(message = "type must not be null")
    val type: ChargePeriodType,

    @field:NotNull(message = "startsOn must not be null")
    val startsOn: LocalDate,

    @field:NotNull(message = "endsOn must not be null")
    val endsOn: LocalDate,
) {
    fun toCommand() = CreateChargePeriodCommand(
        buildingId = BuildingId(buildingId),
        title = title,
        type = type,
        startsOn = startsOn,
        endsOn = endsOn,
    )
}

data class UpdateChargePeriodRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 150, message = "title must be at most 150 characters")
    val title: String,

    @field:NotNull(message = "startsOn must not be null")
    val startsOn: LocalDate,

    @field:NotNull(message = "endsOn must not be null")
    val endsOn: LocalDate,
) {
    fun toCommand() = UpdateChargePeriodCommand(title = title, startsOn = startsOn, endsOn = endsOn)
}

data class AddChargeItemRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,

    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal,

    @field:NotNull(message = "kind must not be null")
    val kind: ChargeItemKind,

    @field:NotNull(message = "allocation must not be null")
    val allocation: CostAllocation,
) {
    fun toCommand() = AddChargeItemCommand(
        title = title,
        amount = amount,
        kind = kind,
        allocation = allocation,
    )
}

data class RegisterInvoicePaymentRequest(
    @field:NotNull(message = "amount must not be null")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than zero")
    val amount: BigDecimal,
) {
    fun toCommand() = RegisterInvoicePaymentCommand(amount = amount)
}

data class ChargePeriodResponse(
    val id: UUID,
    val buildingId: UUID,
    val title: String,
    val type: ChargePeriodType,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val status: ChargePeriodStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(period: ChargePeriod) = ChargePeriodResponse(
            id = period.id.value,
            buildingId = period.buildingId.value,
            title = period.title,
            type = period.type,
            startsOn = period.startsOn,
            endsOn = period.endsOn,
            status = period.status,
            createdAt = period.createdAt,
            updatedAt = period.updatedAt,
        )
    }
}

data class ChargeItemResponse(
    val id: UUID,
    val periodId: UUID,
    val title: String,
    val amount: BigDecimal,
    val kind: ChargeItemKind,
    val allocation: CostAllocation,
    val createdAt: Instant,
) {
    companion object {
        fun from(item: ChargeItem) = ChargeItemResponse(
            id = item.id.value,
            periodId = item.periodId.value,
            title = item.title,
            amount = item.amount,
            kind = item.kind,
            allocation = item.allocation,
            createdAt = item.createdAt,
        )
    }
}

data class UnitInvoiceResponse(
    val id: UUID,
    val periodId: UUID,
    val apartmentId: UUID,
    val amount: BigDecimal,
    val paidAmount: BigDecimal,
    val remaining: BigDecimal,
    val status: InvoiceStatus,
    val issuedAt: Instant,
) {
    companion object {
        fun from(invoice: UnitInvoice) = UnitInvoiceResponse(
            id = invoice.id.value,
            periodId = invoice.periodId.value,
            apartmentId = invoice.apartmentId.value,
            amount = invoice.amount,
            paidAmount = invoice.paidAmount,
            remaining = invoice.remaining,
            status = invoice.status,
            issuedAt = invoice.issuedAt,
        )
    }
}
