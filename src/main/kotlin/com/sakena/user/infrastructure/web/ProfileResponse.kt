package com.sakena.user.infrastructure.web

import com.sakena.user.domain.Role
import java.time.Instant
import java.util.UUID

data class ProfileResponse(
    val id: String,
    val username: String,
    val email: String,
    val role: Role,
    val createdAt: Instant,
    val active: Boolean,
    /** The building this manager administers. Null for every other role. */
    val managedBuildingId: UUID?,
    /**
     * Short-lived URL of the profile picture, or null when none is set — in
     * which case the client keeps showing the user's initial.
     */
    val avatarUrl: String? = null,
)
