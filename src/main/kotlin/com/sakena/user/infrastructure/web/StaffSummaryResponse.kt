package com.sakena.user.infrastructure.web

import com.sakena.user.domain.User

data class StaffSummaryResponse(
    val id: String,
    val username: String,
    val specialty: String?,
    val active: Boolean,
    val averageRating: Double?,
) {
    companion object {
        fun from(user: User, averageRating: Double?) = StaffSummaryResponse(
            id = user.id.value.toString(),
            username = user.username,
            specialty = user.specialty,
            active = user.active,
            averageRating = averageRating,
        )
    }
}
