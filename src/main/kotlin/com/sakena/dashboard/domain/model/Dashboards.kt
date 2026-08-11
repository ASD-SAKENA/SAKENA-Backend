package com.sakena.dashboard.domain.model

import com.sakena.billing.domain.model.InvoiceStatus
import com.sakena.residency.domain.model.TenancyType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Read models of the two dashboards. They are plain data — the numbers are
 * derived from other aggregates rather than owned here, so nothing in this
 * package enforces an invariant.
 */

/** The unit the signed-in resident occupies. */
data class ResidentUnitInfo(
    val buildingName: String,
    val unitNumber: String,
    val floorNumber: Int,
    val areaSquareMeters: BigDecimal,
    val bedrooms: Int,
    val tenancy: TenancyType,
)

/** The resident's most recent invoice. */
data class InvoiceSummary(
    val periodTitle: String,
    val amount: BigDecimal,
    val paidAmount: BigDecimal,
    val remaining: BigDecimal,
    val status: InvoiceStatus,
    val dueOn: LocalDate,
)

data class UpcomingBooking(
    val facilityName: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class ResidentDashboard(
    /** Null while the resident has not been assigned to a unit yet. */
    val unit: ResidentUnitInfo?,
    val walletBalance: BigDecimal,
    val currentInvoice: InvoiceSummary?,
    val openRequestCount: Int,
    val upcomingBookings: List<UpcomingBooking>,
)

/** How much of one charge period has been collected. */
data class PeriodCollection(
    val title: String,
    val endsOn: LocalDate,
    val billed: BigDecimal,
    val collected: BigDecimal,
) {
    val ratePct: Int
        get() = percentage(collected, billed)
}

/** Settlement split of the invoices in the newest issued period. */
data class InvoiceBreakdown(
    val paid: Int,
    val partiallyPaid: Int,
    val unpaid: Int,
) {
    val total: Int get() = paid + partiallyPaid + unpaid
}

data class ManagerDashboard(
    val totalUnits: Int,
    val occupiedUnits: Int,
    val billedThisPeriod: BigDecimal,
    val collectedThisPeriod: BigDecimal,
    /** Collection rate of the newest issued period, and of the one before it. */
    val collectionRatePct: Int,
    val previousCollectionRatePct: Int?,
    val openRequestCount: Int,
    val pendingRequestCount: Int,
    /** Newest issued periods, oldest first, for the collection chart. */
    val periods: List<PeriodCollection>,
    val invoiceBreakdown: InvoiceBreakdown,
)

/** Share of [part] in [whole] as a whole percentage, capped at 100. */
internal fun percentage(part: BigDecimal, whole: BigDecimal): Int {
    if (whole <= BigDecimal.ZERO) return 0
    val pct = part
        .multiply(BigDecimal(100))
        .divide(whole, 0, java.math.RoundingMode.HALF_UP)
        .toInt()
    return pct.coerceIn(0, 100)
}
