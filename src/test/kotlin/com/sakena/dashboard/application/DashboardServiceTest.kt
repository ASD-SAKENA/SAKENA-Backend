package com.sakena.dashboard.application

import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.Building
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.model.Wallet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardServiceTest {

    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val buildingRepository = mockk<BuildingRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val walletRepository = mockk<WalletRepository>()
    private val invoiceRepository = mockk<UnitInvoiceRepository>()
    private val chargePeriodRepository = mockk<ChargePeriodRepository>()
    private val serviceRequestRepository = mockk<ServiceRequestRepository>()
    private val facilityRepository = mockk<FacilityRepository>()
    private val bookingRepository = mockk<FacilityBookingRepository>()

    private val service = DashboardService(
        residencyRepository,
        apartmentRepository,
        buildingRepository,
        buildingAccess,
        walletRepository,
        invoiceRepository,
        chargePeriodRepository,
        serviceRequestRepository,
        facilityRepository,
        bookingRepository,
    )

    private val resident = UserId.generate()
    private val manager = UserId.generate()
    private val building = Building.create("برج ساکنا", "تهران، ونک", manager)
    private val apartment = Apartment.create(building.id, "12", 4, BigDecimal("125.5"), 3)

    @Test
    fun `resident dashboard combines unit, wallet, invoice, requests and bookings`() {
        val period = issuedPeriod("شارژ تیر")
        val invoice = UnitInvoice.issue(period.id, apartment.id, BigDecimal("850000"))
        invoice.registerPayment(BigDecimal("350000"))
        val facility = Facility.create(building.id, "سالن ورزش", "fitness_center")
        val start = Instant.now().plus(2, ChronoUnit.DAYS)

        every { residencyRepository.findActiveByResident(resident) } returns
            Residency.start(apartment.id, resident, TenancyType.OWNER_OCCUPIER)
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { buildingRepository.findById(building.id) } returns building
        every { walletRepository.findByOwner(resident) } returns wallet(BigDecimal("2450000"))
        every { invoiceRepository.findAllByApartment(apartment.id) } returns listOf(invoice)
        every { chargePeriodRepository.findById(period.id) } returns period
        every { serviceRequestRepository.findAllByFilters(any()) } returns
            listOf(openRequest(), settledRequest())
        every { facilityRepository.findAllByBuildingId(building.id) } returns listOf(facility)
        every {
            bookingRepository.findUpcomingByResidentInBuilding(resident, building.id, any())
        } returns
            listOf(
                FacilityBooking.create(
                    facility.id,
                    resident,
                    start,
                    start.plus(1, ChronoUnit.HOURS),
                ),
            )

        val dashboard = service.forResident(resident)

        assertEquals("12", dashboard.unit?.unitNumber)
        assertEquals("برج ساکنا", dashboard.unit?.buildingName)
        assertEquals(TenancyType.OWNER_OCCUPIER, dashboard.unit?.tenancy)
        assertEquals(BigDecimal("2450000"), dashboard.walletBalance)
        assertEquals(BigDecimal("500000"), dashboard.currentInvoice?.remaining)
        assertEquals("شارژ تیر", dashboard.currentInvoice?.periodTitle)
        assertEquals(1, dashboard.openRequestCount)
        assertEquals("سالن ورزش", dashboard.upcomingBookings.single().facilityName)
    }

    @Test
    fun `resident dashboard survives a user with no unit yet`() {
        every { residencyRepository.findActiveByResident(resident) } returns null
        every { walletRepository.findByOwner(resident) } returns null
        every { serviceRequestRepository.findAllByFilters(any()) } returns emptyList()

        val dashboard = service.forResident(resident)

        assertNull(dashboard.unit)
        assertNull(dashboard.currentInvoice)
        assertEquals(BigDecimal.ZERO, dashboard.walletBalance)
        assertEquals(0, dashboard.openRequestCount)
    }

    @Test
    fun `manager dashboard reports occupancy, collection rate and the invoice split`() {
        val previous = issuedPeriod("شارژ خرداد")
        val current = issuedPeriod("شارژ تیر")
        val paid = UnitInvoice.issue(current.id, apartment.id, BigDecimal("1000"))
            .apply { registerPayment(BigDecimal("1000")) }
        val partial = UnitInvoice.issue(current.id, apartment.id, BigDecimal("1000"))
            .apply { registerPayment(BigDecimal("500")) }
        val unpaid = UnitInvoice.issue(current.id, apartment.id, BigDecimal("1000"))

        every { buildingAccess.managedBuildingId(manager) } returns building.id
        every { apartmentRepository.findAllByBuildingId(building.id) } returns listOf(apartment)
        every { residencyRepository.findActiveByApartment(apartment.id) } returns
            Residency.start(apartment.id, resident, TenancyType.TENANT)
        // findAll() is newest first, so the service reverses it for the chart.
        every { chargePeriodRepository.findAll(building.id) } returns listOf(current, previous)
        every { invoiceRepository.findAllByPeriod(current.id) } returns
            listOf(paid, partial, unpaid)
        every { invoiceRepository.findAllByPeriod(previous.id) } returns
            listOf(UnitInvoice.issue(previous.id, apartment.id, BigDecimal("1000")))
        every { serviceRequestRepository.findAllByApartmentIds(setOf(apartment.id)) } returns
            listOf(
                openRequest(apartment.id),
                openRequest(apartment.id),
                settledRequest(apartment.id),
            )

        val dashboard = service.forManager(manager)

        assertEquals(1, dashboard.totalUnits)
        assertEquals(1, dashboard.occupiedUnits)
        assertEquals(BigDecimal("3000"), dashboard.billedThisPeriod)
        assertEquals(BigDecimal("1500"), dashboard.collectedThisPeriod)
        assertEquals(50, dashboard.collectionRatePct)
        assertEquals(0, dashboard.previousCollectionRatePct)
        assertEquals(2, dashboard.openRequestCount)
        assertEquals(listOf("شارژ خرداد", "شارژ تیر"), dashboard.periods.map { it.title })
        assertEquals(1, dashboard.invoiceBreakdown.paid)
        assertEquals(1, dashboard.invoiceBreakdown.partiallyPaid)
        assertEquals(1, dashboard.invoiceBreakdown.unpaid)
        verify(exactly = 1) { apartmentRepository.findAllByBuildingId(building.id) }
        verify(exactly = 1) { chargePeriodRepository.findAll(building.id) }
        verify(exactly = 1) {
            serviceRequestRepository.findAllByApartmentIds(setOf(apartment.id))
        }
    }

    private fun issuedPeriod(title: String): ChargePeriod =
        ChargePeriod.create(
            buildingId = building.id,
            title = title,
            type = ChargePeriodType.MONTHLY,
            startsOn = LocalDate.of(2026, 7, 1),
            endsOn = LocalDate.of(2026, 7, 31),
        ).apply { issue() }

    private fun wallet(balance: BigDecimal): Wallet =
        Wallet.createForUser(resident).apply { credit(balance) }

    private fun openRequest(apartmentId: ApartmentId? = null): ServiceRequest = ServiceRequest.create(
        title = "چکه کردن شیر",
        description = "شیر آشپزخانه چکه می‌کند",
        location = "واحد ۱۲",
        createdBy = resident,
        categoryGroup = ServiceCategoryGroup.FACILITIES,
        subCategory = ServiceSubCategory.PLUMBING,
        requestingApartmentId = apartmentId,
    )

    private fun settledRequest(apartmentId: ApartmentId? = null): ServiceRequest = openRequest(apartmentId).copy(
        status = ServiceRequestStatus.SETTLED,
    )
}
