package com.sakena.user.infrastructure.web

import com.sakena.user.domain.Role
import com.sakena.user.domain.User

data class UserSummaryResponse(
    val id: String,
    val username: String,
    val email: String,
    val role: Role,
    val active: Boolean
) {
    companion object {
        fun from(user: User) = UserSummaryResponse(
            id = user.id.value.toString(),
            username = user.username,
            email = user.email,
            role = user.role,
            active = user.active
        )
    }
}
