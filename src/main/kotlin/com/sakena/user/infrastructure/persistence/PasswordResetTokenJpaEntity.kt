package com.sakena.user.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetTokenJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, unique = true)
    val token: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    val used: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)
