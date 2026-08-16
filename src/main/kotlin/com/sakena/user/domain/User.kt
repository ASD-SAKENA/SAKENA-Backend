package com.sakena.user.domain

import com.sakena.property.domain.model.BuildingId
import java.time.Instant

data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: Role,
    val createdAt: Instant,
    val updatedAt: Instant,
    val active: Boolean = true,
    val specialty: String? = null,
    /** The single building this manager administers. Null for every other role. */
    val managedBuildingId: BuildingId? = null
) {
    init {
        specialty?.let {
            require(it.isNotBlank()) { "Specialty cannot be blank" }
            require(it.length <= MAX_SPECIALTY_LENGTH) {
                "Specialty must be at most $MAX_SPECIALTY_LENGTH characters"
            }
        }
        if (role == Role.MANAGER) {
            require(managedBuildingId != null) { "A manager must administer a building" }
        } else {
            require(managedBuildingId == null) { "Only a manager administers a building" }
        }
    }

    companion object {
        const val MAX_SPECIALTY_LENGTH = 100

        fun register(
            username: String,
            email: String,
            rawPassword: String,
            passwordEncoder: (String) -> String,
            role: Role = Role.RESIDENT,
            managedBuildingId: BuildingId? = null
        ): User {
            require(username.isNotBlank()) { "Username cannot be blank" }
            require(email.isNotBlank() && email.contains("@")) { "Invalid email" }
            require(rawPassword.length >= 8) { "Password must be at least 8 characters" }

            return User(
                id = UserId.generate(),
                username = username.trim(),
                email = email.trim().lowercase(),
                passwordHash = passwordEncoder(rawPassword),
                role = role,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                managedBuildingId = managedBuildingId
            )
        }

        fun reconstitute(
            id: UserId,
            username: String,
            email: String,
            passwordHash: String,
            role: Role,
            createdAt: Instant,
            updatedAt: Instant,
            active: Boolean,
            specialty: String? = null,
            managedBuildingId: BuildingId? = null
        ) = User(id, username, email, passwordHash, role, createdAt, updatedAt, active, specialty, managedBuildingId)
    }

    fun verifyPassword(rawPassword: String, passwordMatcher: (String, String) -> Boolean): Boolean =
        passwordMatcher(rawPassword, passwordHash)

    fun deactivate(): User = copy(active = false, updatedAt = Instant.now())

    fun activate(): User = copy(active = true, updatedAt = Instant.now())

    fun withSpecialty(specialty: String?): User = copy(
        specialty = specialty?.trim()?.takeIf { it.isNotEmpty() },
        updatedAt = Instant.now()
    )

    fun withNewPassword(rawPassword: String, passwordEncoder: (String) -> String): User {
        return this.copy(
            passwordHash = passwordEncoder(rawPassword),
            updatedAt = Instant.now()
        )
    }
}
