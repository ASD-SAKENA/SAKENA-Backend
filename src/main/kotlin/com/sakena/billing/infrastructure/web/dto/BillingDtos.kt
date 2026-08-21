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
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.domain.model.ApartmentId
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

    val targetApartmentId: UUID? = null,
) {
    fun toCommand() = AddChargeItemCommand(
        title = title,
        amount = amount,
        kind = kind,
        allocation = allocation,
        targetApartmentId = targetApartmentId?.let(::ApartmentId),
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
    val targetApartmentId: UUID?,
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
            targetApartmentId = item.targetApartmentId?.value,
            createdAt = item.createdAt,
        )
    }
}

data class UnitInvoiceResponse(
    val id: UUID,
    val periodId: UUID,
    val periodTitle: String,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val apartmentId: UUID,
    val unitNumber: String?,
    val residentUsername: String?,
    val amount: BigDecimal,
    val paidAmount: BigDecimal,
    val remaining: BigDecimal,
    val status: InvoiceStatus,
    val issuedAt: Instant,
) {
    companion object {
        fun from(
            invoice: UnitInvoice,
            periodTitle: String = "",
            startsOn: LocalDate? = null,
            endsOn: LocalDate? = null,
            unitNumber: String? = null,
            residentUsername: String? = null,
        ) = UnitInvoiceResponse(
            id = invoice.id.value,
            periodId = invoice.periodId.value,
            periodTitle = periodTitle,
            startsOn = startsOn,
            endsOn = endsOn,
            apartmentId = invoice.apartmentId.value,
            unitNumber = unitNumber,
            residentUsername = residentUsername,
            amount = invoice.amount,
            paidAmount = invoice.paidAmount,
            remaining = invoice.remaining,
            status = invoice.status,
            issuedAt = invoice.issuedAt,
        )

        fun from(details: com.sakena.billing.application.InvoiceDetails) = from(
            invoice = details.invoice,
            periodTitle = details.periodTitle,
            startsOn = details.startsOn,
            endsOn = details.endsOn,
            unitNumber = details.unitNumber,
            residentUsername = details.residentUsername,
        )
    }
}

data class InvoiceLineItemResponse(
    val id: UUID,
    val title: String,
    val kind: ChargeItemKind,
    val allocation: CostAllocation,
    val totalAmount: BigDecimal,
    val shareAmount: BigDecimal,
) {
    companion object {
        fun from(line: com.sakena.billing.application.InvoiceLineItem) = InvoiceLineItemResponse(
            id = line.item.id.value,
            title = line.item.title,
            kind = line.item.kind,
            allocation = line.item.allocation,
            totalAmount = line.item.amount,
            shareAmount = line.shareAmount,
        )
    }
}

data class ServiceChargeResponse(
    val id: UUID,
    val sourceServiceRequestId: UUID,
    val buildingId: UUID,
    val title: String,
    val amount: BigDecimal,
    val target: ServiceChargeTarget,
    val targetApartmentId: UUID?,
    val createdAt: Instant,
) {
    companion object {
        fun from(charge: ServiceCharge) = ServiceChargeResponse(
            id = charge.id.value,
            sourceServiceRequestId = charge.sourceServiceRequestId.value,
            buildingId = charge.buildingId.value,
            title = charge.title,
            amount = charge.amount,
            target = charge.target,
            targetApartmentId = charge.targetApartmentId?.value,
            createdAt = charge.createdAt,
        )
    }
}
