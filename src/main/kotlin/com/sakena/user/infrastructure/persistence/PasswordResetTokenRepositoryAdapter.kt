package com.sakena.user.infrastructure.persistence

import com.sakena.user.domain.PasswordResetToken
import com.sakena.user.domain.PasswordResetTokenId
import com.sakena.user.domain.PasswordResetTokenRepository
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Repository

@Repository
class PasswordResetTokenRepositoryAdapter(
    private val jpa: PasswordResetTokenRepositoryJpa
) : PasswordResetTokenRepository {

    override fun save(token: PasswordResetToken): PasswordResetToken {
        val entity = toJpaEntity(token)
        val saved = jpa.save(entity)
        return toDomain(saved)
    }

    override fun findByToken(token: String): PasswordResetToken? {
        return jpa.findByToken(token)?.let { toDomain(it) }
    }

    override fun deleteByUserId(userId: UserId) {
        jpa.deleteByUserId(userId.value)
    }

    private fun toJpaEntity(domain: PasswordResetToken): PasswordResetTokenJpaEntity =
        PasswordResetTokenJpaEntity(
            id = domain.id.value,
            userId = domain.userId.value,
            token = domain.token,
            expiresAt = domain.expiresAt,
            used = domain.used,
            createdAt = domain.createdAt
        )

    private fun toDomain(entity: PasswordResetTokenJpaEntity): PasswordResetToken =
        PasswordResetToken(
            id = PasswordResetTokenId(entity.id),
            userId = UserId(entity.userId),
            token = entity.token,
            expiresAt = entity.expiresAt,
            used = entity.used,
            createdAt = entity.createdAt
        )
}
