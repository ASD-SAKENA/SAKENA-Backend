package com.sakena.user.domain

import java.time.Instant

data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: Role,
    val createdAt: Instant,
    val updatedAt: Instant,
    val active: Boolean = true
) {
    companion object {
        fun register(
            username: String,
            email: String,
            rawPassword: String,
            passwordEncoder: (String) -> String,
            role: Role = Role.RESIDENT
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
                updatedAt = Instant.now()
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
            active: Boolean
        ) = User(id, username, email, passwordHash, role, createdAt, updatedAt, active)
    }

    fun verifyPassword(rawPassword: String, passwordMatcher: (String, String) -> Boolean): Boolean =
        passwordMatcher(rawPassword, passwordHash)

    fun deactivate(): User = copy(active = false, updatedAt = Instant.now())

    fun activate(): User = copy(active = true, updatedAt = Instant.now())

    fun withNewPassword(rawPassword: String, passwordEncoder: (String) -> String): User {
        return this.copy(
            passwordHash = passwordEncoder(rawPassword),
            updatedAt = Instant.now()
        )
    }
}
