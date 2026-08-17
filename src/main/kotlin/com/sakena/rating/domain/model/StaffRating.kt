package com.sakena.rating.domain.model

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

/** Value object identifying a [StaffRating]. */
@JvmInline
value class StaffRatingId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): StaffRatingId = StaffRatingId(UUID.randomUUID())
    }
}

/**
 * A resident's 1–5 rating of the staff member who completed their service
 * request. One rating per service request — enforced by a unique constraint
 * at the persistence layer, so there is no update/re-rate path here.
 */
class StaffRating private constructor(
    val id: StaffRatingId,
    val serviceRequestId: ServiceRequestId,
    val staffId: UserId,
    val residentId: UserId,
    val score: Int,
    val createdAt: Instant,
) {
    companion object {
        fun create(
            serviceRequestId: ServiceRequestId,
            staffId: UserId,
            residentId: UserId,
            score: Int,
        ): StaffRating {
            if (score !in 1..5) {
                throw DomainValidationException("Rating score must be between 1 and 5")
            }
            return StaffRating(
                id = StaffRatingId.new(),
                serviceRequestId = serviceRequestId,
                staffId = staffId,
                residentId = residentId,
                score = score,
                createdAt = Instant.now(),
            )
        }

        /** Rebuilds from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: StaffRatingId,
            serviceRequestId: ServiceRequestId,
            staffId: UserId,
            residentId: UserId,
            score: Int,
            createdAt: Instant,
        ): StaffRating = StaffRating(id, serviceRequestId, staffId, residentId, score, createdAt)
    }
}
