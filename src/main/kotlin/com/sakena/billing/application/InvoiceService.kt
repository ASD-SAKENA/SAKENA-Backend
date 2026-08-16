package com.sakena.billing.application

import com.sakena.billing.application.command.RegisterInvoicePaymentCommand
import com.sakena.billing.domain.BillableUnit
import com.sakena.billing.domain.ChargeItemRepository
import com.sakena.billing.domain.ChargePeriodNotFoundException
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.CostAllocationPolicy
import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.UnitInvoiceNotFoundException
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Issues a charge period's cost lines as per-unit invoices and tracks their
 * settlement. Allocation itself is domain logic ([CostAllocationPolicy]); this
 * service only orchestrates it and owns the transaction boundary.
 */
@Service
@Transactional
class InvoiceService(
    private val periodRepository: ChargePeriodRepository,
    private val itemRepository: ChargeItemRepository,
    private val invoiceRepository: UnitInvoiceRepository,
    private val apartmentRepository: ApartmentRepository,
    private val serviceChargeRepository: ServiceChargeRepository,
) {

    fun issue(periodId: ChargePeriodId, requesterManagedBuildingId: BuildingId?): List<UnitInvoice> {
        val period = requirePeriod(periodId)
        if (requesterManagedBuildingId != period.buildingId) {
            throw DomainForbiddenException("You do not manage building '${period.buildingId}'")
        }
        if (invoiceRepository.existsByPeriod(periodId)) {
            throw DomainConflictException("Charge period '${period.title}' has already been issued")
        }

        val pendingServiceCharges = serviceChargeRepository.findPendingByBuilding(
            period.buildingId,
        )
        val serviceChargeItems = pendingServiceCharges.map { it.createChargeItemFor(periodId) }
        val items = itemRepository.findAllByPeriod(periodId) + serviceChargeItems
        if (items.isEmpty()) {
            throw DomainConflictException("Cannot issue a charge period without any cost lines")
        }

        val units = apartmentRepository.findAllByBuildingId(period.buildingId)
            .map { BillableUnit(apartmentId = it.id, areaSquareMeters = it.areaSquareMeters) }
        val shares = CostAllocationPolicy.allocate(items, units)

        // Issuing freezes the cost lines, so the period transitions before the write.
        period.issue()
        serviceChargeItems.forEach(itemRepository::save)
        pendingServiceCharges.forEach { charge ->
            charge.attachTo(periodId)
            serviceChargeRepository.save(charge)
        }
        periodRepository.save(period)

        val invoices = shares
            .filterValues { it > BigDecimal.ZERO }
            .map { (apartmentId, amount) -> UnitInvoice.issue(periodId, apartmentId, amount) }
        return invoiceRepository.saveAll(invoices)
    }

    fun registerPayment(
        invoiceId: UnitInvoiceId,
        command: RegisterInvoicePaymentCommand,
        requesterManagedBuildingId: BuildingId?,
    ): UnitInvoice {
        val invoice = invoiceRepository.findById(invoiceId)
            ?: throw UnitInvoiceNotFoundException(invoiceId)
        val period = requirePeriod(invoice.periodId)
        if (requesterManagedBuildingId != period.buildingId) {
            throw DomainForbiddenException("You do not manage building '${period.buildingId}'")
        }
        invoice.registerPayment(command.amount)
        return invoiceRepository.save(invoice)
    }

    @Transactional(readOnly = true)
    fun getByPeriod(periodId: ChargePeriodId, requesterManagedBuildingId: BuildingId?): List<UnitInvoice> {
        val period = requirePeriod(periodId)
        if (requesterManagedBuildingId != period.buildingId) {
            throw DomainForbiddenException("You do not manage building '${period.buildingId}'")
        }
        return invoiceRepository.findAllByPeriod(periodId)
    }

    @Transactional(readOnly = true)
    fun getByApartment(apartmentId: ApartmentId): List<UnitInvoice> =
        invoiceRepository.findAllByApartment(apartmentId)

    private fun requirePeriod(id: ChargePeriodId): ChargePeriod =
        periodRepository.findById(id) ?: throw ChargePeriodNotFoundException(id)
}
