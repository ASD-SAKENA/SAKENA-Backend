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
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FacilityBookingServiceTest {

    private val facilityRepository = mockk<FacilityRepository>()
    private val bookingRepository = mockk<FacilityBookingRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val zone = ZoneId.of(TIMEZONE)
    private val service = FacilityBookingService(
        facilityRepository,
        bookingRepository,
        buildingAccess,
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

    @BeforeEach
    fun setUpBuildingAccess() {
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
    }

    @Test
    fun `book saves a booking while the slot has free capacity`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.sumPartySizeOverlapping(facility.id, start, end) } returns 9
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
        every { bookingRepository.sumPartySizeOverlapping(facility.id, start, end) } returns 10

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
        every { bookingRepository.sumPartySizeOverlapping(facility.id, start, end) } returns 10
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        service.book(facility.id, BookFacilityCommand(start, end, partySize = 4), resident)

        assertEquals(4, saved.captured.partySize)
    }

    @Test
    fun `book refuses a party that does not fit in the remaining room`() {
        val facility = facility(capacity = 10)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.sumPartySizeOverlapping(facility.id, start, end) } returns 8

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
        every { bookingRepository.sumPartySizeOverlapping(facility.id, start, end) } returns 6
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
        verify(exactly = 0) { bookingRepository.sumPartySizeOverlapping(any(), any(), any()) }
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
        every { bookingRepository.sumPartySizeOverlapping(facility.id, start, end) } returns 0
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
        justRun { bookingRepository.deleteById(booking.id) }

        service.cancel(facility.id, booking.id, resident)

        verify(exactly = 1) { bookingRepository.deleteById(booking.id) }
    }

    @Test
    fun `cancel lets the manager remove anyone's booking`() {
        val facility = facility()
        val booking = FacilityBooking.create(facility.id, resident.id, start, end)
        every { facilityRepository.findByIdAndBuildingId(facility.id, buildingId) } returns facility
        every { bookingRepository.findById(booking.id) } returns booking
        justRun { bookingRepository.deleteById(booking.id) }

        service.cancel(facility.id, booking.id, manager)

        verify(exactly = 1) { bookingRepository.deleteById(booking.id) }
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
}
