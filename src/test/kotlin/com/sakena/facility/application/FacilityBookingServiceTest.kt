package com.sakena.facility.application

import com.sakena.facility.application.command.BookFacilityCommand
import com.sakena.facility.domain.BookingNotFoundException
import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.shared.domain.DomainConflictException
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FacilityBookingServiceTest {

    private val facilityRepository = mockk<FacilityRepository>()
    private val bookingRepository = mockk<FacilityBookingRepository>()
    private val service = FacilityBookingService(facilityRepository, bookingRepository)

    private val resident = UserId.generate()
    private val start: Instant = Instant.now().truncatedTo(ChronoUnit.HOURS)
    private val end: Instant = start.plus(1, ChronoUnit.HOURS)

    @Test
    fun `book saves a booking while the slot has free capacity`() {
        val facility = Facility.create("Pool", "pool", capacity = 10)
        every { facilityRepository.findById(facility.id) } returns facility
        every { bookingRepository.countOverlapping(facility.id, start, end) } returns 9
        val saved = slot<FacilityBooking>()
        every { bookingRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.book(facility.id, BookFacilityCommand(start, end), resident)

        assertEquals(facility.id, result.facilityId)
        assertEquals(resident, result.bookedBy)
        verify(exactly = 1) { bookingRepository.save(any()) }
    }

    @Test
    fun `book locks the slot once capacity is reached`() {
        val facility = Facility.create("Pool", "pool", capacity = 10)
        every { facilityRepository.findById(facility.id) } returns facility
        every { bookingRepository.countOverlapping(facility.id, start, end) } returns 10

        assertFailsWith<DomainConflictException> {
            service.book(facility.id, BookFacilityCommand(start, end), resident)
        }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `cancel rejects a booking that belongs to someone else`() {
        val facility = Facility.create("Pool", "pool")
        val booking = FacilityBooking.create(facility.id, UserId.generate(), start, end)
        every { bookingRepository.findById(booking.id) } returns booking

        assertFailsWith<DomainConflictException> {
            service.cancel(facility.id, booking.id, resident)
        }
    }

    @Test
    fun `cancel removes the resident's own booking`() {
        val facility = Facility.create("Pool", "pool")
        val booking = FacilityBooking.create(facility.id, resident, start, end)
        every { bookingRepository.findById(booking.id) } returns booking
        justRun { bookingRepository.deleteById(booking.id) }

        service.cancel(facility.id, booking.id, resident)

        verify(exactly = 1) { bookingRepository.deleteById(booking.id) }
    }

    @Test
    fun `cancel throws when the booking is missing`() {
        val facility = Facility.create("Pool", "pool")
        val booking = FacilityBooking.create(facility.id, resident, start, end)
        every { bookingRepository.findById(booking.id) } returns null

        assertFailsWith<BookingNotFoundException> {
            service.cancel(facility.id, booking.id, resident)
        }
    }
}
