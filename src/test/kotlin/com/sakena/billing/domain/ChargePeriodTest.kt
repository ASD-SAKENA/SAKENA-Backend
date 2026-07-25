package com.sakena.billing.domain

import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChargePeriodTest {

    private val buildingId = BuildingId.new()
    private val start: LocalDate = LocalDate.of(2025, 6, 22)
    private val end: LocalDate = LocalDate.of(2025, 7, 22)

    private fun period() = ChargePeriod.create(
        buildingId = buildingId,
        title = "  Tir charge  ",
        type = ChargePeriodType.MONTHLY,
        startsOn = start,
        endsOn = end,
    )

    @Test
    fun `create trims the title and starts as an editable draft`() {
        val period = period()

        assertEquals("Tir charge", period.title)
        assertEquals(ChargePeriodStatus.DRAFT, period.status)
        assertTrue(period.editable)
    }

    @Test
    fun `create rejects a range that does not move forward`() {
        assertFailsWith<DomainValidationException> {
            ChargePeriod.create(buildingId, "Bad", ChargePeriodType.CUSTOM, end, start)
        }
    }

    @Test
    fun `issuing freezes the period and closing follows`() {
        val period = period()

        period.issue()
        assertEquals(ChargePeriodStatus.ISSUED, period.status)
        assertTrue(!period.editable)

        period.close()
        assertEquals(ChargePeriodStatus.CLOSED, period.status)
    }

    @Test
    fun `an issued period can no longer be rescheduled`() {
        val period = period()
        period.issue()

        assertFailsWith<DomainConflictException> { period.reschedule("New", start, end) }
    }

    @Test
    fun `a closed period cannot be issued again`() {
        val period = period()
        period.issue()
        period.close()

        assertFailsWith<DomainConflictException> { period.issue() }
    }
}

class UnitInvoiceTest {

    private val periodId = ChargePeriodId.new()
    private val apartmentId = ApartmentId.new()

    private fun invoice() = UnitInvoice.issue(periodId, apartmentId, BigDecimal("850000"))

    @Test
    fun `a fresh invoice is unpaid and owes its full amount`() {
        val invoice = invoice()

        assertEquals(com.sakena.billing.domain.model.InvoiceStatus.UNPAID, invoice.status)
        assertEquals(BigDecimal("850000"), invoice.remaining)
    }

    @Test
    fun `a partial payment moves the invoice to partially paid`() {
        val invoice = invoice()

        invoice.registerPayment(BigDecimal("350000"))

        assertEquals(com.sakena.billing.domain.model.InvoiceStatus.PARTIALLY_PAID, invoice.status)
        assertEquals(BigDecimal("500000"), invoice.remaining)
    }

    @Test
    fun `paying the remainder settles the invoice`() {
        val invoice = invoice()

        invoice.registerPayment(BigDecimal("850000"))

        assertEquals(com.sakena.billing.domain.model.InvoiceStatus.PAID, invoice.status)
        assertFailsWith<DomainConflictException> { invoice.registerPayment(BigDecimal.ONE) }
    }

    @Test
    fun `overpaying is rejected`() {
        val invoice = invoice()

        assertFailsWith<DomainConflictException> { invoice.registerPayment(BigDecimal("900000")) }
    }
}
