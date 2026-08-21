package com.sakena.billing.application

import com.sakena.billing.application.command.RegisterInvoicePaymentCommand
import com.sakena.billing.domain.ChargeItemRepository
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import com.sakena.wallet.application.WalletService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvoiceServiceTest {

    private val periodRepository = mockk<ChargePeriodRepository>(relaxed = true)
    private val itemRepository = mockk<ChargeItemRepository>()
    private val invoiceRepository = mockk<UnitInvoiceRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val serviceChargeRepository = mockk<ServiceChargeRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val walletService = mockk<WalletService>()
    private val userRepository = mockk<com.sakena.user.domain.UserRepository>(relaxed = true)
    private val service = InvoiceService(
        periodRepository,
        itemRepository,
        invoiceRepository,
        apartmentRepository,
        serviceChargeRepository,
        buildingAccess,
        residencyRepository,
        walletService,
        userRepository,
    )

    private val managerId = UserId.generate()
    private val buildingId = BuildingId.new()

    private fun period() = ChargePeriod.create(
        buildingId = buildingId,
        title = "Tir charge",
        type = ChargePeriodType.MONTHLY,
        startsOn = LocalDate.of(2025, 6, 22),
        endsOn = LocalDate.of(2025, 7, 22),
    )

    private fun apartment(unitNumber: String, area: String) = Apartment.create(
        buildingId = buildingId,
        unitNumber = unitNumber,
        floorNumber = 1,
        areaSquareMeters = BigDecimal(area),
        bedrooms = 2,
    )

    @Test
    fun `issue allocates every cost line across the building's units`() {
        val period = period()
        allowManager(period)
        val units = listOf(apartment("1", "50"), apartment("2", "50"))
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns false
        every { serviceChargeRepository.findPendingByBuilding(buildingId) } returns emptyList()
        every { itemRepository.findAllByPeriod(period.id) } returns listOf(
            ChargeItem.create(
                period.id,
                "Monthly charge",
                BigDecimal("900000"),
                ChargeItemKind.RECURRING_CHARGE,
                CostAllocation.EQUAL,
            ),
        )
        every { apartmentRepository.findAllByBuildingId(buildingId) } returns units
        val saved = slot<List<UnitInvoice>>()
        every { invoiceRepository.saveAll(capture(saved)) } answers { saved.captured }

        val invoices = service.issue(period.id, managerId)

        assertEquals(2, invoices.size)
        assertEquals(0, BigDecimal("450000").compareTo(invoices.first().amount))
        assertEquals(ChargePeriodStatus.ISSUED, period.status)
        verify(exactly = 1) { periodRepository.save(period) }
    }

    @Test
    fun `issuing twice is rejected`() {
        val period = period()
        allowManager(period)
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns true

        assertFailsWith<DomainConflictException> { service.issue(period.id, managerId) }
    }

    @Test
    fun `issuing a period without cost lines is rejected`() {
        val period = period()
        allowManager(period)
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns false
        every { serviceChargeRepository.findPendingByBuilding(buildingId) } returns emptyList()
        every { itemRepository.findAllByPeriod(period.id) } returns emptyList()

        assertFailsWith<DomainConflictException> { service.issue(period.id, managerId) }
    }

    @Test
    fun `issue rejects a period outside the manager's building`() {
        val period = period()
        every { periodRepository.findById(period.id) } returns period
        every { buildingAccess.requireManagerAccess(buildingId, managerId) } throws
            DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.issue(period.id, managerId) }
        verify(exactly = 0) { invoiceRepository.saveAll(any()) }
    }

    @Test
    fun `registerPayment settles the invoice through the aggregate`() {
        val period = period()
        allowManager(period)
        val invoice = UnitInvoice.issue(period.id, apartment("1", "50").id, BigDecimal("500000"))
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.save(any()) } answers { firstArg() }

        val result = service.registerPayment(
            invoice.id,
            RegisterInvoicePaymentCommand(BigDecimal("500000")),
            managerId,
        )

        assertEquals(BigDecimal("500000"), result.paidAmount)
    }

    @Test
    fun `issue imports pending shared and targeted service costs`() {
        val period = period()
        allowManager(period)
        val units = listOf(apartment("1", "50"), apartment("2", "50"))
        val shared = serviceCharge(
            amount = "100",
            target = ServiceChargeTarget.ALL_UNITS,
        )
        val targeted = serviceCharge(
            amount = "75",
            target = ServiceChargeTarget.SPECIFIC_UNIT,
            targetApartmentId = units.first().id,
        )
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns false
        every { serviceChargeRepository.findPendingByBuilding(buildingId) } returns
            listOf(shared, targeted)
        every { itemRepository.findAllByPeriod(period.id) } returns emptyList()
        every { apartmentRepository.findAllByBuildingId(buildingId) } returns units
        every { itemRepository.save(any()) } answers { firstArg() }
        every { serviceChargeRepository.save(any()) } answers { firstArg() }
        every { periodRepository.save(any()) } answers { firstArg() }
        val saved = slot<List<UnitInvoice>>()
        every { invoiceRepository.saveAll(capture(saved)) } answers { saved.captured }

        val invoices = service.issue(period.id, managerId).associateBy { it.apartmentId }

        assertEquals(0, BigDecimal("125").compareTo(invoices.getValue(units.first().id).amount))
        assertEquals(0, BigDecimal("50").compareTo(invoices.getValue(units.last().id).amount))
        assertEquals(period.id, shared.attachedPeriodId)
        assertEquals(period.id, targeted.attachedPeriodId)
        verify(exactly = 2) { itemRepository.save(any()) }
        verify(exactly = 2) { serviceChargeRepository.save(any()) }
    }

    @Test
    fun `manager apartment history rejects a unit in another building`() {
        val unit = apartment("1", "50")
        every { apartmentRepository.findById(unit.id) } returns unit
        every { buildingAccess.requireManagerAccess(buildingId, managerId) } throws
            DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.getByApartment(unit.id, managerId)
        }
        verify(exactly = 0) { invoiceRepository.findAllByApartment(any()) }
    }

    @Test
    fun `resident apartment history is limited to their active unit`() {
        val ownUnit = apartment("1", "50")
        val anotherUnit = apartment("2", "50")
        every { residencyRepository.findActiveByResident(managerId) } returns
            Residency.start(ownUnit.id, managerId, TenancyType.TENANT)

        assertFailsWith<DomainForbiddenException> {
            service.getOwnApartment(anotherUnit.id, managerId)
        }
        verify(exactly = 0) { invoiceRepository.findAllByApartment(any()) }
    }

    @Test
    fun `getLineItems returns this unit's share of each cost line`() {
        val period = period().also { it.issue() }
        val unitA = apartment("1", "50")
        val unitB = apartment("2", "50")
        val residentId = UserId.generate()
        val invoice = UnitInvoice.issue(period.id, unitA.id, BigDecimal("450000"))
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { residencyRepository.findActiveByResident(residentId) } returns
            Residency.start(unitA.id, residentId, TenancyType.TENANT)
        every { periodRepository.findById(period.id) } returns period
        every { itemRepository.findAllByPeriod(period.id) } returns listOf(
            ChargeItem.create(
                period.id,
                "Monthly charge",
                BigDecimal("900000"),
                ChargeItemKind.RECURRING_CHARGE,
                CostAllocation.EQUAL,
            ),
            ChargeItem.create(
                period.id,
                "Unit A only",
                BigDecimal("100000"),
                ChargeItemKind.EXTRAORDINARY_EXPENSE,
                CostAllocation.SPECIFIC_UNIT,
                unitA.id,
            ),
            ChargeItem.create(
                period.id,
                "Unit B only",
                BigDecimal("50000"),
                ChargeItemKind.FACILITY_COST,
                CostAllocation.SPECIFIC_UNIT,
                unitB.id,
            ),
        )
        every { apartmentRepository.findAllByBuildingId(buildingId) } returns listOf(unitA, unitB)

        val lines = service.getLineItems(invoice.id, residentId)

        assertEquals(2, lines.size)
        assertEquals("Monthly charge", lines[0].item.title)
        assertEquals(0, BigDecimal("450000").compareTo(lines[0].shareAmount))
        assertEquals("Unit A only", lines[1].item.title)
        assertEquals(0, BigDecimal("100000").compareTo(lines[1].shareAmount))
    }

    @Test
    fun `getLineItems rejects another unit's invoice`() {
        val period = period()
        val ownUnit = apartment("1", "50")
        val otherUnit = apartment("2", "50")
        val residentId = UserId.generate()
        val invoice = UnitInvoice.issue(period.id, otherUnit.id, BigDecimal("100"))
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { residencyRepository.findActiveByResident(residentId) } returns
            Residency.start(ownUnit.id, residentId, TenancyType.TENANT)

        assertFailsWith<DomainForbiddenException> {
            service.getLineItems(invoice.id, residentId)
        }
    }

    @Test
    fun `getOutstanding returns unpaid invoices for issued periods`() {
        val issued = period().also { it.issue() }
        val unit = apartment("1", "80")
        val unpaid = UnitInvoice.issue(issued.id, unit.id, BigDecimal("100000"))
        val paid = UnitInvoice.issue(issued.id, apartment("2", "80").id, BigDecimal("100000")).also {
            it.registerPayment(BigDecimal("100000"))
        }
        every { buildingAccess.managedBuildingId(managerId) } returns buildingId
        every { periodRepository.findAll(buildingId) } returns listOf(issued)
        every { invoiceRepository.findAllByPeriod(issued.id) } returns listOf(unpaid, paid)
        every { apartmentRepository.findById(unit.id) } returns unit
        every { residencyRepository.findActiveByApartment(unit.id) } returns null

        val result = service.getOutstanding(managerId, null)

        assertEquals(1, result.size)
        assertEquals(unpaid.id, result[0].invoice.id)
        assertEquals("1", result[0].unitNumber)
        assertEquals(issued.title, result[0].periodTitle)
    }

    private fun allowManager(period: ChargePeriod) {
        every { buildingAccess.requireManagerAccess(period.buildingId, managerId) } returns Unit
    }

    private fun serviceCharge(
        amount: String,
        target: ServiceChargeTarget,
        targetApartmentId: com.sakena.property.domain.model.ApartmentId? = null,
    ) = ServiceCharge.create(
        sourceServiceRequestId = ServiceRequestId.generate(),
        buildingId = buildingId,
        title = "Service cost",
        amount = BigDecimal(amount),
        target = target,
        targetApartmentId = targetApartmentId,
    )
}
