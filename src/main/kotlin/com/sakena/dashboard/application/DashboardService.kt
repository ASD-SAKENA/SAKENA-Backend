package com.sakena.dashboard.application

import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.InvoiceStatus
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.dashboard.domain.model.InvoiceBreakdown
import com.sakena.dashboard.domain.model.InvoiceSummary
import com.sakena.dashboard.domain.model.ManagerDashboard
import com.sakena.dashboard.domain.model.PeriodCollection
import com.sakena.dashboard.domain.model.ResidentDashboard
import com.sakena.dashboard.domain.model.ResidentUnitInfo
import com.sakena.dashboard.domain.model.UpcomingBooking
import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.FacilityRepository
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestFilters
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

/**
 * Builds the two dashboard read models. It only reads — every number here is
 * owned by another context's aggregate, so this service composes their ports
 * instead of holding state or business rules of its own.
 */
@Service
@Transactional(readOnly = true)
class DashboardService(
    private val residencyRepository: ResidencyRepository,
    private val apartmentRepository: ApartmentRepository,
    private val buildingRepository: BuildingRepository,
    private val buildingAccess: BuildingAccess,
    private val walletRepository: WalletRepository,
    private val invoiceRepository: UnitInvoiceRepository,
    private val chargePeriodRepository: ChargePeriodRepository,
    private val serviceRequestRepository: ServiceRequestRepository,
    private val facilityRepository: FacilityRepository,
    private val bookingRepository: FacilityBookingRepository,
) {

    fun forResident(residentId: UserId): ResidentDashboard {
        val residency = residencyRepository.findActiveByResident(residentId)
        val apartment = residency?.let { apartmentRepository.findById(it.apartmentId) }
        val building = apartment?.let { buildingRepository.findById(it.buildingId) }

        val unit = if (residency != null && apartment != null) {
            ResidentUnitInfo(
                buildingName = building?.name ?: "",
                unitNumber = apartment.unitNumber,
                floorNumber = apartment.floorNumber,
                areaSquareMeters = apartment.areaSquareMeters,
                bedrooms = apartment.bedrooms,
                tenancy = residency.tenancy,
            )
        } else {
            null
        }

        val openRequests = serviceRequestRepository
            .findAllByFilters(ServiceRequestFilters(createdBy = residentId))
            .count { it.status.isOpen() }

        val facilities = building
            ?.let { facilityRepository.findAllByBuildingId(it.id) }
            .orEmpty()
            .associateBy { it.id }
        val bookings = building
            ?.let {
                bookingRepository.findUpcomingByResidentInBuilding(
                    residentId,
                    it.id,
                    Instant.now(),
                )
            }
            .orEmpty()
            .mapNotNull { booking ->
                facilities[booking.facilityId]?.let {
                    UpcomingBooking(it.name, booking.startsAt, booking.endsAt)
                }
            }

        return ResidentDashboard(
            unit = unit,
            walletBalance = walletRepository.findByOwner(residentId)?.balance ?: BigDecimal.ZERO,
            currentInvoice = apartment?.let { currentInvoiceOf(it.id) },
            openRequestCount = openRequests,
            upcomingBookings = bookings,
        )
    }

    fun forManager(managerId: UserId): ManagerDashboard {
        val buildingId = buildingAccess.managedBuildingId(managerId)
        val apartments = apartmentRepository.findAllByBuildingId(buildingId)
        val occupied = apartments.count {
            residencyRepository.findActiveByApartment(it.id) != null
        }

        // Oldest first, so the chart reads left to right like a timeline.
        val periods = chargePeriodRepository
            .findAll(buildingId)
            .filter { it.status != ChargePeriodStatus.DRAFT }
            .take(CHART_PERIODS)
            .reversed()
        val collections = periods.map(::collectionOf)

        val requests = serviceRequestRepository.findAllByApartmentIds(
            apartments.mapTo(mutableSetOf()) { it.id },
        )

        val latestPeriod = periods.lastOrNull()
        val latestInvoices = latestPeriod
            ?.let { invoiceRepository.findAllByPeriod(it.id) }
            .orEmpty()

        return ManagerDashboard(
            totalUnits = apartments.size,
            occupiedUnits = occupied,
            billedThisPeriod = collections.lastOrNull()?.billed ?: BigDecimal.ZERO,
            collectedThisPeriod = collections.lastOrNull()?.collected ?: BigDecimal.ZERO,
            collectionRatePct = collections.lastOrNull()?.ratePct ?: 0,
            previousCollectionRatePct = collections.dropLast(1).lastOrNull()?.ratePct,
            openRequestCount = requests.count { it.status.isOpen() },
            pendingRequestCount = requests.count { it.status == ServiceRequestStatus.PENDING },
            periods = collections,
            invoiceBreakdown = breakdownOf(latestInvoices),
        )
    }

    private fun currentInvoiceOf(apartmentId: ApartmentId): InvoiceSummary? {
        val invoice = invoiceRepository.findAllByApartment(apartmentId)
            .maxByOrNull { it.issuedAt }
            ?: return null
        val period = chargePeriodRepository.findById(invoice.periodId) ?: return null
        return InvoiceSummary(
            periodTitle = period.title,
            amount = invoice.amount,
            paidAmount = invoice.paidAmount,
            remaining = invoice.remaining,
            status = invoice.status,
            dueOn = period.endsOn,
        )
    }

    private fun collectionOf(period: ChargePeriod): PeriodCollection {
        val invoices = invoiceRepository.findAllByPeriod(period.id)
        return PeriodCollection(
            title = period.title,
            endsOn = period.endsOn,
            billed = invoices.fold(BigDecimal.ZERO) { sum, it -> sum + it.amount },
            collected = invoices.fold(BigDecimal.ZERO) { sum, it -> sum + it.paidAmount },
        )
    }

    private fun breakdownOf(invoices: List<UnitInvoice>) = InvoiceBreakdown(
        paid = invoices.count { it.status == InvoiceStatus.PAID },
        partiallyPaid = invoices.count { it.status == InvoiceStatus.PARTIALLY_PAID },
        unpaid = invoices.count { it.status == InvoiceStatus.UNPAID },
    )

    private companion object {
        /** How many charge periods the collection chart shows. */
        const val CHART_PERIODS = 6
    }
}

/** A request still needing someone's attention. */
private fun ServiceRequestStatus.isOpen(): Boolean =
    this != ServiceRequestStatus.COMPLETED &&
        this != ServiceRequestStatus.CONFIRMED &&
        this != ServiceRequestStatus.SETTLED &&
        this != ServiceRequestStatus.REJECTED
