package com.sakena.billing.domain

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

/**
 * Outbound ports for the billing context. Declared in the domain layer and
 * implemented by adapters in infrastructure.
 */
interface ChargePeriodRepository {
    fun save(period: ChargePeriod): ChargePeriod

    fun findById(id: ChargePeriodId): ChargePeriod?

    /** Periods of a building (or all of them), newest start date first. */
    fun findAll(buildingId: BuildingId?): List<ChargePeriod>

    fun deleteById(id: ChargePeriodId)
}

interface ChargeItemRepository {
    fun save(item: ChargeItem): ChargeItem

    fun findById(id: ChargeItemId): ChargeItem?

    fun findAllByPeriod(periodId: ChargePeriodId): List<ChargeItem>

    fun deleteById(id: ChargeItemId)
}

interface ServiceChargeRepository {
    fun save(charge: ServiceCharge): ServiceCharge

    fun findById(id: ServiceChargeId): ServiceCharge?

    fun findBySourceServiceRequestId(serviceRequestId: ServiceRequestId): ServiceCharge?

    fun findPendingByBuilding(buildingId: BuildingId): List<ServiceCharge>
}

interface UnitInvoiceRepository {
    fun saveAll(invoices: List<UnitInvoice>): List<UnitInvoice>

    fun save(invoice: UnitInvoice): UnitInvoice

    fun findById(id: UnitInvoiceId): UnitInvoice?

    fun findAllByPeriod(periodId: ChargePeriodId): List<UnitInvoice>

    fun findAllByApartment(apartmentId: ApartmentId): List<UnitInvoice>

    fun existsByPeriod(periodId: ChargePeriodId): Boolean
}
