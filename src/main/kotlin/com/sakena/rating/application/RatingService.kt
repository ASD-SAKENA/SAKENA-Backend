package com.sakena.rating.application

import com.sakena.rating.domain.RatingRepository
import com.sakena.rating.domain.model.StaffRating
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class RatingService(
    private val ratingRepository: RatingRepository,
) {

    fun rate(serviceRequestId: ServiceRequestId, staffId: UserId, residentId: UserId, score: Int): StaffRating =
        ratingRepository.save(StaffRating.create(serviceRequestId, staffId, residentId, score))

    @Transactional(readOnly = true)
    fun getAverageFor(staffIds: List<UserId>): Map<UserId, Double> =
        ratingRepository.findAverageByStaffIds(staffIds)
}
