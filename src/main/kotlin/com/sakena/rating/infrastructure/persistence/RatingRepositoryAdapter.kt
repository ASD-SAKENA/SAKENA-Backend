package com.sakena.rating.infrastructure.persistence

import com.sakena.rating.domain.RatingRepository
import com.sakena.rating.domain.model.StaffRating
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Component

/** Adapter implementing the domain [RatingRepository] port. */
@Component
class RatingRepositoryAdapter(
    private val jpaRepository: StaffRatingJpaRepository,
) : RatingRepository {

    override fun save(rating: StaffRating): StaffRating {
        jpaRepository.save(
            StaffRatingEntity(
                id = rating.id.value,
                serviceRequestId = rating.serviceRequestId.value,
                staffId = rating.staffId.value,
                residentId = rating.residentId.value,
                score = rating.score,
                createdAt = rating.createdAt,
            ),
        )
        return rating
    }

    override fun findAverageByStaffIds(staffIds: List<UserId>): Map<UserId, Double> {
        if (staffIds.isEmpty()) return emptyMap()
        return jpaRepository.findAverageByStaffIds(staffIds.map { it.value })
            .associate { UserId(it.getStaffId()) to it.getAverage() }
    }
}
