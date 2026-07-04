package com.sakena.user.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class PasswordResetTokenTest {

    @Test
    fun `requestNewToken should create token with expiration`() {
        val userId = UserId.generate()
        val token = PasswordResetToken.requestNewToken(userId, "abc123", 30)

        assertEquals(userId, token.userId)
        assertEquals("abc123", token.token)
        assertFalse(token.used)
        assertNotNull(token.id)
        assertTrue(token.expiresAt.isAfter(Instant.now()))
        assertTrue(token.expiresAt.isBefore(Instant.now().plusSeconds(31 * 60)))
    }

    @Test
    fun `isValid should be true for unused and not expired token`() {
        val token = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = UserId.generate(),
            token = "valid",
            expiresAt = Instant.now().plusSeconds(60),
            used = false
        )
        assertTrue(token.isValid())
    }

    @Test
    fun `isValid should be false if token is used`() {
        val token = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = UserId.generate(),
            token = "used",
            expiresAt = Instant.now().plusSeconds(60),
            used = true
        )
        assertFalse(token.isValid())
    }

    @Test
    fun `isValid should be false if token is expired`() {
        val token = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = UserId.generate(),
            token = "expired",
            expiresAt = Instant.now().minusSeconds(1),
            used = false
        )
        assertFalse(token.isValid())
    }

    @Test
    fun `markUsed should set used to true`() {
        val token = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = UserId.generate(),
            token = "mark",
            expiresAt = Instant.now().plusSeconds(60),
            used = false
        )
        val marked = token.markUsed()
        assertTrue(marked.used)
        assertNotEquals(token, marked)
    }
}
