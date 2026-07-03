package com.sakena.user.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PasswordResetTokenRepositoryJpa : JpaRepository<PasswordResetTokenJpaEntity, UUID> {
    fun findByToken(token: String): PasswordResetTokenJpaEntity?
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetTokenJpaEntity t WHERE t.userId = :userId")
    fun deleteByUserId(userId: UUID)
}
