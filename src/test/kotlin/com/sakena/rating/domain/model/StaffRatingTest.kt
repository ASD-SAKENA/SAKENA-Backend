package com.sakena.rating.domain.model

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StaffRatingTest {

    private val serviceRequestId = ServiceRequestId.generate()
    private val staffId = UserId.generate()
    private val residentId = UserId.generate()

    @Test
    fun `create builds a rating with the given score`() {
        val rating = StaffRating.create(serviceRequestId, staffId, residentId, score = 4)

        assertEquals(serviceRequestId, rating.serviceRequestId)
        assertEquals(staffId, rating.staffId)
        assertEquals(residentId, rating.residentId)
        assertEquals(4, rating.score)
    }

    @Test
    fun `create rejects a score below 1`() {
        assertFailsWith<DomainValidationException> {
            StaffRating.create(serviceRequestId, staffId, residentId, score = 0)
        }
    }

    @Test
    fun `create rejects a score above 5`() {
        assertFailsWith<DomainValidationException> {
            StaffRating.create(serviceRequestId, staffId, residentId, score = 6)
        }
    }

    @Test
    fun `create accepts every score from 1 to 5`() {
        (1..5).forEach { score ->
            val rating = StaffRating.create(serviceRequestId, staffId, residentId, score)
            assertEquals(score, rating.score)
        }
    }
}
