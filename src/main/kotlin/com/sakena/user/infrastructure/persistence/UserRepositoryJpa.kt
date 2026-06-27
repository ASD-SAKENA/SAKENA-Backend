package com.sakena.user.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepositoryJpa : JpaRepository<UserJpaEntity, UUID> {
    fun findByUsername(username: String): UserJpaEntity?
    fun findByEmail(email: String): UserJpaEntity?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
