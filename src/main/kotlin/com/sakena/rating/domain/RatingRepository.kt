package com.sakena.rating.domain

import com.sakena.rating.domain.model.StaffRating
import com.sakena.user.domain.UserId

/** Outbound port for the rating context. */
interface RatingRepository {
    fun save(rating: StaffRating): StaffRating

    /** Average score per staff id, for the ids that have at least one rating. */
    fun findAverageByStaffIds(staffIds: List<UserId>): Map<UserId, Double>
}
