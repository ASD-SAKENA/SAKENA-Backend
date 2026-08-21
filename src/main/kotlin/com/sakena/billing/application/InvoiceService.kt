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
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.property.domain.ApartmentNotFoundException
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.wallet.application.WalletService
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
    private val buildingAccess: BuildingAccess,
    private val residencyRepository: ResidencyRepository,
    private val walletService: WalletService,
    private val userRepository: UserRepository,
) {

    fun issue(periodId: ChargePeriodId, managerId: UserId): List<UnitInvoice> {
        val period = requireManagedPeriod(periodId, managerId)
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
        managerId: UserId,
    ): UnitInvoice {
        val invoice = invoiceRepository.findById(invoiceId)
            ?: throw UnitInvoiceNotFoundException(invoiceId)
        requireManagedPeriod(invoice.periodId, managerId)
        invoice.registerPayment(command.amount)
        return invoiceRepository.save(invoice)
    }

    @Transactional(readOnly = true)
    fun getByPeriod(periodId: ChargePeriodId, managerId: UserId): List<UnitInvoice> {
        requireManagedPeriod(periodId, managerId)
        return invoiceRepository.findAllByPeriod(periodId)
    }

    @Transactional(readOnly = true)
    fun getByApartment(apartmentId: ApartmentId, managerId: UserId): List<UnitInvoice> {
        val apartment = requireApartment(apartmentId)
        buildingAccess.requireManagerAccess(apartment.buildingId, managerId)
        return invoiceRepository.findAllByApartment(apartmentId)
    }

    @Transactional(readOnly = true)
    fun getOwnApartment(apartmentId: ApartmentId, residentId: UserId): List<UnitInvoice> {
        val residency = residencyRepository.findActiveByResident(residentId)
        if (residency?.apartmentId != apartmentId) {
            throw DomainForbiddenException("You may only access invoices for your own apartment")
        }
        return invoiceRepository.findAllByApartment(apartmentId)
    }

    /** Every invoice for the signed-in resident's current unit, newest first. */
    @Transactional(readOnly = true)
    fun getMine(residentId: UserId): List<UnitInvoice> {
        val residency = residencyRepository.findActiveByResident(residentId)
            ?: return emptyList()
        return invoiceRepository.findAllByApartment(residency.apartmentId)
    }

    /**
     * Cost lines that make up a resident's invoice, with that unit's allocated
     * share. Only lines that contributed a positive share are returned.
     */
    @Transactional(readOnly = true)
    fun getLineItems(invoiceId: UnitInvoiceId, residentId: UserId): List<InvoiceLineItem> {
        val invoice = invoiceRepository.findById(invoiceId)
            ?: throw UnitInvoiceNotFoundException(invoiceId)
        val residency = residencyRepository.findActiveByResident(residentId)
            ?: throw DomainForbiddenException("You must be an active resident to view invoice details")
        if (residency.apartmentId != invoice.apartmentId) {
            throw DomainForbiddenException("You may only view invoices for your own apartment")
        }

        val period = requirePeriod(invoice.periodId)
        val items = itemRepository.findAllByPeriod(period.id)
        if (items.isEmpty()) return emptyList()

        val units = apartmentRepository.findAllByBuildingId(period.buildingId)
            .map { BillableUnit(apartmentId = it.id, areaSquareMeters = it.areaSquareMeters) }

        return items.mapNotNull { item ->
            val share = CostAllocationPolicy.allocate(listOf(item), units)[invoice.apartmentId]
                ?: BigDecimal.ZERO
            if (share <= BigDecimal.ZERO) null
            else InvoiceLineItem(item = item, shareAmount = share)
        }
    }

    @Transactional(readOnly = true)
    fun periodOf(invoice: UnitInvoice): ChargePeriod? =
        periodRepository.findById(invoice.periodId)

    /**
     * Issued invoices still carrying a balance — optionally narrowed to one
     * charge period. Used by the manager to chase unpaid units.
     */
    @Transactional(readOnly = true)
    fun getOutstanding(managerId: UserId, periodId: ChargePeriodId?): List<InvoiceDetails> {
        val periods = if (periodId != null) {
            listOf(requireManagedPeriod(periodId, managerId))
        } else {
            val buildingId = buildingAccess.managedBuildingId(managerId)
            periodRepository.findAll(buildingId)
                .filter { it.status != ChargePeriodStatus.DRAFT }
        }
        return periods
            .flatMap { period ->
                invoiceRepository.findAllByPeriod(period.id)
                    .filter { it.remaining > BigDecimal.ZERO }
                    .map { invoice -> detailsOf(invoice, period) }
            }
            .sortedByDescending { it.invoice.issuedAt }
    }

    @Transactional(readOnly = true)
    fun detailsOf(invoice: UnitInvoice): InvoiceDetails =
        detailsOf(invoice, periodOf(invoice))

    private fun detailsOf(invoice: UnitInvoice, period: ChargePeriod?): InvoiceDetails {
        val apartment = apartmentRepository.findById(invoice.apartmentId)
        val residentId = residencyRepository.findActiveByApartment(invoice.apartmentId)?.residentId
        val residentUsername = residentId?.let { userRepository.findById(it)?.username }
        return InvoiceDetails(
            invoice = invoice,
            periodTitle = period?.title.orEmpty(),
            startsOn = period?.startsOn,
            endsOn = period?.endsOn,
            unitNumber = apartment?.unitNumber,
            residentUsername = residentUsername,
        )
    }

    /**
     * Resident pays an outstanding invoice from their personal wallet balance.
     * Settles immediately — no manager review queue.
     */
    fun payFromWallet(
        invoiceId: UnitInvoiceId,
        residentId: UserId,
        amount: BigDecimal?,
    ): UnitInvoice {
        val invoice = invoiceRepository.findById(invoiceId)
            ?: throw UnitInvoiceNotFoundException(invoiceId)
        val residency = residencyRepository.findActiveByResident(residentId)
            ?: throw DomainForbiddenException("You must be an active resident to pay an invoice")
        if (residency.apartmentId != invoice.apartmentId) {
            throw DomainForbiddenException("You may only pay invoices for your own apartment")
        }
        val period = requirePeriod(invoice.periodId)
        val payAmount = amount ?: invoice.remaining
        invoice.registerPayment(payAmount)
        walletService.payInvoiceFromWallet(
            buildingId = period.buildingId,
            residentId = residentId,
            amount = payAmount,
            description = "پرداخت «${period.title}» از کیف پول",
        )
        return invoiceRepository.save(invoice)
    }

    private fun requireManagedPeriod(id: ChargePeriodId, managerId: UserId): ChargePeriod {
        val period = requirePeriod(id)
        buildingAccess.requireManagerAccess(period.buildingId, managerId)
        return period
    }

    private fun requireApartment(id: ApartmentId): Apartment =
        apartmentRepository.findById(id) ?: throw ApartmentNotFoundException(id)

    private fun requirePeriod(id: ChargePeriodId): ChargePeriod =
        periodRepository.findById(id) ?: throw ChargePeriodNotFoundException(id)
}
