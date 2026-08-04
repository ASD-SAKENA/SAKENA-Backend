package com.sakena.billing.infrastructure.persistence

import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeId
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.servicerequest.domain.ServiceRequestId

/** Translates between the billing aggregates and their JPA representations. */
internal object BillingEntityMappers {

    fun toEntity(period: ChargePeriod): ChargePeriodEntity =
        ChargePeriodEntity(
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

    fun toDomain(entity: ChargePeriodEntity): ChargePeriod =
        ChargePeriod.reconstitute(
            id = ChargePeriodId(entity.id),
            buildingId = BuildingId(entity.buildingId),
            title = entity.title,
            type = entity.type,
            startsOn = entity.startsOn,
            endsOn = entity.endsOn,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(item: ChargeItem): ChargeItemEntity =
        ChargeItemEntity(
            id = item.id.value,
            periodId = item.periodId.value,
            title = item.title,
            amount = item.amount,
            kind = item.kind,
            allocation = item.allocation,
            createdAt = item.createdAt,
        )

    fun toDomain(entity: ChargeItemEntity): ChargeItem =
        ChargeItem.reconstitute(
            id = ChargeItemId(entity.id),
            periodId = ChargePeriodId(entity.periodId),
            title = entity.title,
            amount = entity.amount,
            kind = entity.kind,
            allocation = entity.allocation,
            createdAt = entity.createdAt,
        )

    fun toEntity(charge: ServiceCharge): ServiceChargeEntity =
        ServiceChargeEntity(
            id = charge.id.value,
            sourceServiceRequestId = charge.sourceServiceRequestId.value,
            buildingId = charge.buildingId.value,
            title = charge.title,
            amount = charge.amount,
            target = charge.target,
            targetApartmentId = charge.targetApartmentId?.value,
            chargePeriodId = charge.attachedPeriodId?.value,
            createdAt = charge.createdAt,
            attachedAt = charge.attachedAt,
        )

    fun toDomain(entity: ServiceChargeEntity): ServiceCharge =
        ServiceCharge.reconstitute(
            id = ServiceChargeId(entity.id),
            sourceServiceRequestId = ServiceRequestId(entity.sourceServiceRequestId),
            buildingId = BuildingId(entity.buildingId),
            title = entity.title,
            amount = entity.amount,
            target = entity.target,
            targetApartmentId = entity.targetApartmentId?.let(::ApartmentId),
            attachedPeriodId = entity.chargePeriodId?.let(::ChargePeriodId),
            createdAt = entity.createdAt,
            attachedAt = entity.attachedAt,
        )

    fun toEntity(invoice: UnitInvoice): UnitInvoiceEntity =
        UnitInvoiceEntity(
            id = invoice.id.value,
            periodId = invoice.periodId.value,
            apartmentId = invoice.apartmentId.value,
            amount = invoice.amount,
            paidAmount = invoice.paidAmount,
            issuedAt = invoice.issuedAt,
            updatedAt = invoice.updatedAt,
        )

    fun toDomain(entity: UnitInvoiceEntity): UnitInvoice =
        UnitInvoice.reconstitute(
            id = UnitInvoiceId(entity.id),
            periodId = ChargePeriodId(entity.periodId),
            apartmentId = ApartmentId(entity.apartmentId),
            amount = entity.amount,
            paidAmount = entity.paidAmount,
            issuedAt = entity.issuedAt,
            updatedAt = entity.updatedAt,
        )
}
