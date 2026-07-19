package com.sakena.user.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class UserTest {

    private fun createTestUser(
        username: String = "test",
        email: String = "test@example.com",
        passwordHash: String = "hashed_password",
        role: Role = Role.RESIDENT,
        active: Boolean = true
    ): User {
        return User.reconstitute(
            id = UserId.generate(),
            username = username,
            email = email,
            passwordHash = passwordHash,
            role = role,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = active
        )
    }

    // --- Registration tests ---
    @Test
    fun `register should create a valid user with hashed password`() {
        val rawPassword = "secret123" // length >=8
        val encoder: (String) -> String = { "hashed_$it" }

        val user = User.register(
            username = "john_doe",
            email = "john@example.com",
            rawPassword = rawPassword,
            passwordEncoder = encoder,
            role = Role.MANAGER
        )

        assertEquals("john_doe", user.username)
        assertEquals("john@example.com", user.email)
        assertEquals("hashed_secret123", user.passwordHash)
        assertEquals(Role.MANAGER, user.role)
        assertTrue(user.active)
        assertNotNull(user.id)
        assertNotNull(user.createdAt)
        assertNotNull(user.updatedAt)
        assertTrue(user.createdAt <= Instant.now())
        assertTrue(user.updatedAt <= Instant.now())
    }

    @Test
    fun `register should default to RESIDENT role if not specified`() {
        val user = User.register(
            username = "jane",
            email = "jane@test.com",
            rawPassword = "password123",
            passwordEncoder = { it }
        )
        assertEquals(Role.RESIDENT, user.role)
    }

    @Test
    fun `register should fail if username is blank`() {
        assertThrows<IllegalArgumentException> {
            User.register("", "test@test.com", "password123", { it })
        }
    }

    @Test
    fun `register should fail if email is invalid`() {
        assertThrows<IllegalArgumentException> {
            User.register("user", "invalid-email", "password123", { it })
        }
    }

    @Test
    fun `register should fail if password is shorter than 8 characters`() {
        assertThrows<IllegalArgumentException> {
            User.register("user", "valid@email.com", "1234567", { it })
        }
    }

    // --- Password verification tests ---
    @Test
    fun `verifyPassword should return true if password matches`() {
        val passwordHash = "hashed_secret"
        val user = createTestUser(passwordHash = passwordHash)
        val matcher: (String, String) -> Boolean = { raw, hash ->
            raw == "secret" && hash == passwordHash
        }
        assertTrue(user.verifyPassword("secret", matcher))
    }

    @Test
    fun `verifyPassword should return false if password does not match`() {
        val passwordHash = "hashed_secret"
        val user = createTestUser(passwordHash = passwordHash)
        val matcher: (String, String) -> Boolean = { raw, hash ->
            raw == "secret" && hash == passwordHash
        }
        assertFalse(user.verifyPassword("wrong", matcher))
    }

    // --- Active status tests ---
    @Test
    fun `deactivate should set active to false and update updatedAt`() {
        val user = createTestUser(active = true)
        Thread.sleep(1)
        val deactivated = user.deactivate()
        assertFalse(deactivated.active)
        assertTrue(deactivated.updatedAt > user.updatedAt)
    }

    @Test
    fun `activate should set active to true and update updatedAt`() {
        val user = createTestUser(active = false)
        Thread.sleep(1)
        val activated = user.activate()
        assertTrue(activated.active)
        assertTrue(activated.updatedAt > user.updatedAt)
    }

    @Test
    fun `withNewPassword should update passwordHash and updatedAt`() {
        val user = createTestUser(passwordHash = "old_hash")
        val now = Instant.now()
        Thread.sleep(1) // ensure time difference
        val updated = user.withNewPassword("newPassword123", { "new_hash" })
        assertEquals("new_hash", updated.passwordHash)
        assertTrue(updated.updatedAt > user.updatedAt)
    }
}
