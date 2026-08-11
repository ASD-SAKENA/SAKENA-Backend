package com.sakena.dashboard.infrastructure.web.dto

import com.sakena.billing.domain.model.InvoiceStatus
import com.sakena.dashboard.domain.model.ManagerDashboard
import com.sakena.dashboard.domain.model.ResidentDashboard
import com.sakena.residency.domain.model.TenancyType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ResidentUnitResponse(
    val buildingName: String,
    val unitNumber: String,
    val floorNumber: Int,
    val areaSquareMeters: BigDecimal,
    val bedrooms: Int,
    val tenancy: TenancyType,
)

data class InvoiceSummaryResponse(
    val periodTitle: String,
    val amount: BigDecimal,
    val paidAmount: BigDecimal,
    val remaining: BigDecimal,
    val status: InvoiceStatus,
    val dueOn: LocalDate,
)

data class UpcomingBookingResponse(
    val facilityName: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class ResidentDashboardResponse(
    val unit: ResidentUnitResponse?,
    val walletBalance: BigDecimal,
    val currentInvoice: InvoiceSummaryResponse?,
    val openRequestCount: Int,
    val upcomingBookings: List<UpcomingBookingResponse>,
) {
    companion object {
        fun from(dashboard: ResidentDashboard) = ResidentDashboardResponse(
            unit = dashboard.unit?.let {
                ResidentUnitResponse(
                    buildingName = it.buildingName,
                    unitNumber = it.unitNumber,
                    floorNumber = it.floorNumber,
                    areaSquareMeters = it.areaSquareMeters,
                    bedrooms = it.bedrooms,
                    tenancy = it.tenancy,
                )
            },
            walletBalance = dashboard.walletBalance,
            currentInvoice = dashboard.currentInvoice?.let {
                InvoiceSummaryResponse(
                    periodTitle = it.periodTitle,
                    amount = it.amount,
                    paidAmount = it.paidAmount,
                    remaining = it.remaining,
                    status = it.status,
                    dueOn = it.dueOn,
                )
            },
            openRequestCount = dashboard.openRequestCount,
            upcomingBookings = dashboard.upcomingBookings.map {
                UpcomingBookingResponse(it.facilityName, it.startsAt, it.endsAt)
            },
        )
    }
}

data class PeriodCollectionResponse(
    val title: String,
    val endsOn: LocalDate,
    val billed: BigDecimal,
    val collected: BigDecimal,
    val ratePct: Int,
)

data class InvoiceBreakdownResponse(
    val paid: Int,
    val partiallyPaid: Int,
    val unpaid: Int,
    val total: Int,
)

data class ManagerDashboardResponse(
    val totalUnits: Int,
    val occupiedUnits: Int,
    val billedThisPeriod: BigDecimal,
    val collectedThisPeriod: BigDecimal,
    val collectionRatePct: Int,
    val previousCollectionRatePct: Int?,
    val openRequestCount: Int,
    val pendingRequestCount: Int,
    val periods: List<PeriodCollectionResponse>,
    val invoiceBreakdown: InvoiceBreakdownResponse,
) {
    companion object {
        fun from(dashboard: ManagerDashboard) = ManagerDashboardResponse(
            totalUnits = dashboard.totalUnits,
            occupiedUnits = dashboard.occupiedUnits,
            billedThisPeriod = dashboard.billedThisPeriod,
            collectedThisPeriod = dashboard.collectedThisPeriod,
            collectionRatePct = dashboard.collectionRatePct,
            previousCollectionRatePct = dashboard.previousCollectionRatePct,
            openRequestCount = dashboard.openRequestCount,
            pendingRequestCount = dashboard.pendingRequestCount,
            periods = dashboard.periods.map {
                PeriodCollectionResponse(
                    title = it.title,
                    endsOn = it.endsOn,
                    billed = it.billed,
                    collected = it.collected,
                    ratePct = it.ratePct,
                )
            },
            invoiceBreakdown = InvoiceBreakdownResponse(
                paid = dashboard.invoiceBreakdown.paid,
                partiallyPaid = dashboard.invoiceBreakdown.partiallyPaid,
                unpaid = dashboard.invoiceBreakdown.unpaid,
                total = dashboard.invoiceBreakdown.total,
            ),
        )
    }
}
