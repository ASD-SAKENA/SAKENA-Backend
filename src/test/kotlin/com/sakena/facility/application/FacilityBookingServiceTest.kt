package com.sakena.facility.application

import com.sakena.facility.application.command.BookFacilityCommand
import com.sakena.facility.domain.BookingNotFoundException
import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.BookingRules
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import com.sakena.wallet.domain.model.Wallet
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FacilityBookingServiceTest {

    private val facilityRepository = mockk<FacilityRepository>()
    private val bookingRepository = mockk<FacilityBookingRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val walletRepository = mockk<com.sakena.wallet.domain.WalletRepository>(relaxed = true)
    private val transactionRepository =
        mockk<com.sakena.wallet.domain.WalletTransactionRepository>(relaxed = true)
    private val zone = ZoneId.of(TIMEZONE)
    private val service = FacilityBookingService(
        facilityRepository,
        bookingRepository,
        buildingAccess,
        walletRepository,
        transactionRepository,
        TIMEZONE,
    )

    private val buildingId = BuildingId.new()
    private val resident = user(Role.RESIDENT)
    private val manager = user(Role.MANAGER)

    /** Tomorrow 10:00–11:00 local time — inside the default 08–22 window. */
    private val start: Instant = Instant.now()
        .atZone(zone)
        .plusDays(1)
        .withHour(10)
        .truncatedTo(ChronoUnit.HOURS)
        .toInstant()
    private val end: Instant = start.plus(1, ChronoUnit.HOURS)

    /** Existing bookings covering the whole [start, end) window. */
    private fun occupying(vararg partySizes: Int): List<FacilityBooking> =
        partySizes.map { FacilityBooking.create(FacilityId(UUID.randomUUID()), UserId(UUID.randomUUID()), start, end, partySize = it) }

    @BeforeEach
    fun setUpBuildingAccess() {
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
    }

    @Test
    fun `book saves a booking while the slot has free capacity`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns occupying(9)
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.book(facility.id, BookFacilityCommand(start, end), resident)

        assertEquals(facility.id, result.facilityId)
        assertEquals(resident.id, result.bookedBy)
        verify(exactly = 1) { bookingRepository.save(any()) }
    }

    @Test
    fun `book locks the slot once capacity is reached`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns occupying(10)

        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(start, end), resident)
        }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `capacity counts people, so many small bookings still leave room`() {
        // The bug: ten one-person bookings used to fill a twenty-person
        // facility, because each booking counted as one against capacity.
        val facility = facility(capacity = 20)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns occupying(10)
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        service.book(facility.id, BookFacilityCommand(start, end, partySize = 4), resident)

        assertEquals(4, saved.captured.partySize)
    }

    @Test
    fun `book refuses a party that does not fit in the remaining room`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns occupying(8)

        // Two seats left, four people asked for.
        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(start, end, partySize = 4), resident)
        }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `a party filling the last of the room is accepted`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns occupying(6)
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        service.book(facility.id, BookFacilityCommand(start, end, partySize = 4), resident)

        assertEquals(4, saved.captured.partySize)
    }

    @Test
    fun `book refuses a party larger than the facility itself`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility

        assertFailsWith<DomainValidationException> {
            service.book(facility.id, BookFacilityCommand(start, end, partySize = 11), resident)
        }
        // Rejected before the slot is even queried — it can never fit.
        verify(exactly = 0) { bookingRepository.findAllForFacilityBetween(any(), any(), any()) }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `book rejects a slot outside the opening hours`() {
        val facility = facility(rules = rules(opensAt = LocalTime.of(18, 0)))
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility

        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(start, end), resident)
        }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `book rejects a slot in the past`() {
        val facility = facility()
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        val past = start.minus(3, ChronoUnit.DAYS)

        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(past, past.plus(1, ChronoUnit.HOURS)), resident)
        }
    }

    @Test
    fun `book rejects a resident who has used up the weekly quota`() {
        val facility = facility(rules = rules(maxPerResidentPerWeek = 2))
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every {
            bookingRepository.countByResidentBetween(facility.id, resident.id, any(), any())
        } returns 2

        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(start, end), resident)
        }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `book allows a resident still inside the weekly quota`() {
        val facility = facility(rules = rules(maxPerResidentPerWeek = 2))
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every {
            bookingRepository.countByResidentBetween(facility.id, resident.id, any(), any())
        } returns 1
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns emptyList()
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        service.book(facility.id, BookFacilityCommand(start, end), resident)

        verify(exactly = 1) { bookingRepository.save(any()) }
    }

    @Test
    fun `quote prices a slot from the hourly rate`() {
        val facility = facility(rules = rules(hourlyPrice = BigDecimal("50000")))
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility

        assertEquals(BigDecimal("50000"), service.quote(facility.id, start, end, resident))
    }

    @Test
    fun `cancel rejects a booking that belongs to someone else`() {
        val facility = facility()
        val booking = FacilityBooking.create(facility.id, UserId.generate(), start, end)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns booking

        assertFailsWith<DomainConflictException> {
            service.cancel(facility.id, booking.id, resident)
        }
    }

    @Test
    fun `cancel removes the resident's own booking`() {
        val facility = facility()
        val booking = FacilityBooking.create(facility.id, resident.id, start, end)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns booking
        every { bookingRepository.save(any()) } answers { firstArg() }

        service.cancel(facility.id, booking.id, resident)

        // Kept, not deleted, so the occupancy history survives.
        assertTrue(booking.cancelled)
        verify(exactly = 1) { bookingRepository.save(booking) }
    }

    @Test
    fun `cancel lets the manager remove anyone's booking`() {
        val facility = facility()
        val booking = FacilityBooking.create(facility.id, resident.id, start, end)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns booking
        every { bookingRepository.save(any()) } answers { firstArg() }

        service.cancel(facility.id, booking.id, manager)

        assertTrue(booking.cancelled)
        verify(exactly = 1) { bookingRepository.save(booking) }
    }

    @Test
    fun `cancel throws when the booking is missing`() {
        val facility = facility()
        val booking = FacilityBooking.create(facility.id, resident.id, start, end)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns null

        assertFailsWith<BookingNotFoundException> {
            service.cancel(facility.id, booking.id, resident)
        }
    }

    @Test
    fun `resident cannot book a facility outside their building`() {
        val facilityId = FacilityId.new()
        every { facilityRepository.findByIdAndBuildingId(facilityId, buildingId) } returns null

        assertFailsWith<FacilityNotFoundException> {
            service.book(facilityId, BookFacilityCommand(start, end), resident)
        }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `resident upcoming bookings are queried only in their current building`() {
        val booking = FacilityBooking.create(facility().id, resident.id, start, end)
        every {
            bookingRepository.findUpcomingByResidentInBuilding(resident.id, buildingId, any())
        } returns listOf(booking)

        assertEquals(listOf(booking), service.getUpcomingFor(resident))
    }

    @Test
    fun `resident cannot list bookings for a facility outside their building`() {
        val facilityId = FacilityId.new()
        every { facilityRepository.findByIdAndBuildingId(facilityId, buildingId) } returns null

        assertFailsWith<FacilityNotFoundException> {
            service.getForFacilityBetween(facilityId, start, end, resident)
        }
        verify(exactly = 0) { bookingRepository.findAllForFacilityBetween(any(), any(), any()) }
    }

    @Test
    fun `manager cannot cancel a booking for a facility outside their building`() {
        val foreignFacility = Facility.create(BuildingId.new(), "Foreign pool", "pool")
        val booking = FacilityBooking.create(foreignFacility.id, resident.id, start, end)
        every {
            facilityRepository.findByIdAndBuildingId(foreignFacility.id, buildingId)
        } returns null

        assertFailsWith<FacilityNotFoundException> {
            service.cancel(foreignFacility.id, booking.id, manager)
        }
        verify(exactly = 0) { bookingRepository.findById(any()) }
        verify(exactly = 0) { bookingRepository.deleteById(any()) }
    }

    private fun facility(
        capacity: Int = 10,
        rules: BookingRules = BookingRules.DEFAULT,
    ) = Facility.create(buildingId, "Pool", "pool", capacity = capacity, rules = rules)

    private fun rules(
        opensAt: LocalTime = LocalTime.of(8, 0),
        maxPerResidentPerWeek: Int = 0,
        hourlyPrice: BigDecimal = BigDecimal.ZERO,
    ) = BookingRules.DEFAULT.copy(
        opensAt = opensAt,
        maxPerResidentPerWeek = maxPerResidentPerWeek,
        hourlyPrice = hourlyPrice,
    )

    private fun user(role: Role) = User(
        id = UserId.generate(),
        username = "user-${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        passwordHash = "hash",
        role = role,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        managedBuildingId = buildingId.takeIf { role == Role.MANAGER },
    )

    private companion object {
        const val TIMEZONE = "Asia/Tehran"
    }

    @Test
    fun `a bigger party pays proportionally more for the same slot`() {
        val paid = rules(hourlyPrice = BigDecimal("50000"))

        // One hour: 50,000 per person.
        assertEquals(BigDecimal("50000"), paid.priceFor(start, end, partySize = 1))
        assertEquals(BigDecimal("200000"), paid.priceFor(start, end, partySize = 4))
    }

    @Test
    fun `booking takes the price from the resident and gives it to the building`() {
        val facility = facility(rules = rules(hourlyPrice = BigDecimal("50000")))
        val residentWallet = Wallet.createForUser(resident.id)
        residentWallet.credit(BigDecimal("500000"))
        val buildingWallet = Wallet.createBuilding(buildingId)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns emptyList()
        every { bookingRepository.save(any()) } answers { firstArg() }
        every { walletRepository.findByOwner(resident.id) } returns residentWallet
        every { walletRepository.findBuildingWallet(buildingId) } returns buildingWallet

        service.book(facility.id, BookFacilityCommand(start, end, partySize = 2), resident)

        // Two people for an hour at 50,000 each.
        assertEquals(BigDecimal("400000"), residentWallet.balance)
        assertEquals(BigDecimal("100000"), buildingWallet.balance)
    }

    @Test
    fun `a resident who cannot afford the slot does not get a booking`() {
        val facility = facility(rules = rules(hourlyPrice = BigDecimal("50000")))
        val residentWallet = Wallet.createForUser(resident.id)
        residentWallet.credit(BigDecimal("10000"))
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns emptyList()
        every { bookingRepository.save(any()) } answers { firstArg() }
        every { walletRepository.findByOwner(resident.id) } returns residentWallet
        every { walletRepository.findBuildingWallet(buildingId) } returns Wallet.createBuilding(buildingId)

        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(start, end), resident)
        }
    }

    @Test
    fun `cancelling before the session returns the money to the resident`() {
        val facility = facility(rules = rules(hourlyPrice = BigDecimal("50000")))
        val booking = FacilityBooking.create(
            facility.id, resident.id, start, end, partySize = 2, price = BigDecimal("100000"),
        )
        val residentWallet = Wallet.createForUser(resident.id)
        val buildingWallet = Wallet.createBuilding(buildingId)
        buildingWallet.credit(BigDecimal("100000"))
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns booking
        every { bookingRepository.save(any()) } answers { firstArg() }
        every { walletRepository.findByOwner(resident.id) } returns residentWallet
        every { walletRepository.findBuildingWallet(buildingId) } returns buildingWallet

        service.cancel(facility.id, booking.id, resident)

        // Exactly what was taken comes back — not a recomputed price.
        assertEquals(BigDecimal("100000"), residentWallet.balance)
        assertEquals(BigDecimal.ZERO, buildingWallet.balance)
    }

    @Test
    fun `a session that has already started cannot be cancelled`() {
        val facility = facility()
        val startedAt = Instant.now().minus(10, ChronoUnit.MINUTES)
        val booking = FacilityBooking.create(
            facility.id, resident.id, startedAt, startedAt.plus(1, ChronoUnit.HOURS),
        )
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns booking

        assertFailsWith<DomainConflictException> {
            service.cancel(facility.id, booking.id, resident)
        }
        // No refund for time the resident already had the facility.
        verify(exactly = 0) { walletRepository.save(any()) }
    }

    @Test
    fun `a booking earlier in the day does not block a later free slot`() {
        // The bug: an 08:00-09:00 booking used to count against a 09:00-10:00
        // request, because occupancy was summed across the range rather than
        // measured at a moment.
        val facility = facility(capacity = 10)
        val earlier = FacilityBooking.create(
            facility.id, UserId(UUID.randomUUID()),
            start.minus(1, ChronoUnit.HOURS), start, partySize = 10,
        )
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns listOf(earlier)
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        service.book(facility.id, BookFacilityCommand(start, end, partySize = 10), resident)

        assertEquals(10, saved.captured.partySize)
    }

    @Test
    fun `a cancelled booking frees its seats again`() {
        val facility = facility(capacity = 10)
        val cancelled = FacilityBooking.create(
            facility.id, UserId(UUID.randomUUID()), start, end, partySize = 10,
        )
        cancelled.cancel()
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findAllForFacilityBetween(facility.id, start, end) } returns listOf(cancelled)
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        service.book(facility.id, BookFacilityCommand(start, end, partySize = 10), resident)

        assertEquals(10, saved.captured.partySize)
    }
}
