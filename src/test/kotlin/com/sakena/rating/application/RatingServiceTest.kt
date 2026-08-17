package com.sakena.rating.application

import com.sakena.rating.domain.RatingRepository
import com.sakena.rating.domain.model.StaffRating
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RatingServiceTest {

    private val ratingRepository = mockk<RatingRepository>()
    private val service = RatingService(ratingRepository)

    @Test
    fun `rate saves a new rating for the completed request`() {
        val serviceRequestId = ServiceRequestId.generate()
        val staffId = UserId.generate()
        val residentId = UserId.generate()
        val saved = slot<StaffRating>()
        every { ratingRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.rate(serviceRequestId, staffId, residentId, score = 5)

        assertEquals(serviceRequestId, result.serviceRequestId)
        assertEquals(staffId, result.staffId)
        assertEquals(5, result.score)
        verify(exactly = 1) { ratingRepository.save(any()) }
    }

    @Test
    fun `getAverageFor delegates to the repository`() {
        val staffA = UserId.generate()
        val staffB = UserId.generate()
        every { ratingRepository.findAverageByStaffIds(listOf(staffA, staffB)) } returns
            mapOf(staffA to 4.5)

        val result = service.getAverageFor(listOf(staffA, staffB))

        assertEquals(mapOf(staffA to 4.5), result)
    }
}
