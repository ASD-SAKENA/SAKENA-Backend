package com.sakena.user.infrastructure.persistence

import com.sakena.user.domain.Role
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Id
    val id: UUID,

    @Column(unique = true, nullable = false)
    var username: String,

    @Column(unique = true, nullable = false)
    var email: String,

    @Column(nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    @Column(nullable = false)
    var createdAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(length = 100)
    var specialty: String? = null
)
