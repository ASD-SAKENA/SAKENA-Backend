package com.sakena.user.domain

interface PasswordResetTokenRepository {
    fun save(token: PasswordResetToken): PasswordResetToken
    fun findByToken(token: String): PasswordResetToken?
    fun deleteByUserId(userId: UserId)
}
