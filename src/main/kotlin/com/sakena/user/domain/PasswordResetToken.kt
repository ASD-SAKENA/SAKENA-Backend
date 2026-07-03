package com.sakena.user.domain

import java.time.Instant
import java.util.UUID

data class PasswordResetToken(
    val id: PasswordResetTokenId,
    val userId: UserId,
    val token: String,
    val expiresAt: Instant,
    val used: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    companion object {
        fun requestNewToken(
            userId: UserId,
            token: String = UUID.randomUUID().toString(),
            expiresAfterMinutes: Long = 60
        ): PasswordResetToken {
            return PasswordResetToken(
                id = PasswordResetTokenId.generate(),
                userId = userId,
                token = token,
                expiresAt = Instant.now().plusSeconds(expiresAfterMinutes * 60)
            )
        }
    }

    fun isValid(): Boolean = !used && expiresAt.isAfter(Instant.now())

    fun markUsed(): PasswordResetToken = this.copy(used = true)
}

@JvmInline
value class PasswordResetTokenId(val value: UUID) {
    companion object {
        fun generate() = PasswordResetTokenId(UUID.randomUUID())
        fun fromString(id: String) = PasswordResetTokenId(UUID.fromString(id))
    }
}
