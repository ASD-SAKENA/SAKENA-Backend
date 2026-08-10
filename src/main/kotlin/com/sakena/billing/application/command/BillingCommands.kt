package com.sakena.billing.application.command

import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.domain.model.ApartmentId
import java.math.BigDecimal
import java.time.LocalDate

data class CreateChargePeriodCommand(
    val buildingId: BuildingId,
    val title: String,
    val type: ChargePeriodType,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
)

data class UpdateChargePeriodCommand(
    val title: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
)

data class AddChargeItemCommand(
    val title: String,
    val amount: BigDecimal,
    val kind: ChargeItemKind,
    val allocation: CostAllocation,
    val targetApartmentId: ApartmentId? = null,
)

data class RegisterInvoicePaymentCommand(
    val amount: BigDecimal,
)
