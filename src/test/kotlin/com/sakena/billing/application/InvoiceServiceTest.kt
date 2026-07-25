package com.sakena.billing.application

import com.sakena.billing.application.command.RegisterInvoicePaymentCommand
import com.sakena.billing.domain.ChargeItemRepository
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
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
    private val service = InvoiceService(
        periodRepository,
        itemRepository,
        invoiceRepository,
        apartmentRepository,
    )

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
        val units = listOf(apartment("1", "50"), apartment("2", "50"))
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns false
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

        val invoices = service.issue(period.id)

        assertEquals(2, invoices.size)
        assertEquals(BigDecimal("450000.00"), invoices.first().amount)
        assertEquals(ChargePeriodStatus.ISSUED, period.status)
        verify(exactly = 1) { periodRepository.save(period) }
    }

    @Test
    fun `issuing twice is rejected`() {
        val period = period()
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns true

        assertFailsWith<DomainConflictException> { service.issue(period.id) }
    }

    @Test
    fun `issuing a period without cost lines is rejected`() {
        val period = period()
        every { periodRepository.findById(period.id) } returns period
        every { invoiceRepository.existsByPeriod(period.id) } returns false
        every { itemRepository.findAllByPeriod(period.id) } returns emptyList()

        assertFailsWith<DomainConflictException> { service.issue(period.id) }
    }

    @Test
    fun `registerPayment settles the invoice through the aggregate`() {
        val period = period()
        val invoice = UnitInvoice.issue(period.id, apartment("1", "50").id, BigDecimal("500000"))
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { invoiceRepository.save(any()) } answers { firstArg() }

        val result = service.registerPayment(
            invoice.id,
            RegisterInvoicePaymentCommand(BigDecimal("500000")),
        )

        assertEquals(BigDecimal("500000"), result.paidAmount)
    }
}
