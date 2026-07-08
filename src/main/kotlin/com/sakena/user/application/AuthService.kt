package com.sakena.user.application

import com.sakena.shared.domain.DomainException
import com.sakena.user.domain.PasswordResetToken
import com.sakena.user.domain.PasswordResetTokenRepository
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import com.sakena.user.domain.exceptions.InvalidCredentialsException
import com.sakena.user.domain.exceptions.TokenInvalidException
import com.sakena.user.domain.exceptions.UserAlreadyExistsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailSender: EmailSender,
    @Value("\${app.frontend-url:http://localhost:3000}")
    private val frontendUrl: String,
    @Value("\${app.reset-token-expiration-minutes:60}")
    private var resetTokenExpMinutes: Long
) {

    fun register(command: RegisterCommand): User {
        if (userRepository.existsByUsername(command.username)) {
            throw UserAlreadyExistsException("username", command.username)
        }
        if (userRepository.existsByEmail(command.email)) {
            throw UserAlreadyExistsException("email", command.email)
        }

        val role = command.role?.let { Role.valueOf(it.uppercase()) } ?: Role.RESIDENT

        val user = User.register(
            username = command.username,
            email = command.email,
            rawPassword = command.password,
            passwordEncoder = { passwordEncoder.encode(it) },
            role = role
        )

        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username)

    fun login(command: LoginCommand): String {
        val user = userRepository.findByUsername(command.username)
            ?: throw InvalidCredentialsException()

        if (!user.verifyPassword(command.password, passwordEncoder::matches)) {
            throw InvalidCredentialsException()
        }

        if (!user.active) {
            throw DomainException("User account is inactive")
        }

        return jwtTokenProvider.generateToken(user.username, user.role.name)
    }

    fun forgotPassword(command: ForgotPasswordCommand) {
        val user = userRepository.findByEmail(command.email)
            ?: return  // security: don't reveal if email exists

        // Optionally remove any existing tokens for this user
        passwordResetTokenRepository.deleteByUserId(user.id)

        val resetToken = PasswordResetToken.requestNewToken(
            userId = user.id,
            expiresAfterMinutes = resetTokenExpMinutes
        )
        passwordResetTokenRepository.save(resetToken)

        // Send email with reset link
        val resetLink = "$frontendUrl/reset-password?token=${resetToken.token}"
        emailSender.sendPasswordResetEmail(user.email, resetLink)
    }

    fun resetPassword(command: ResetPasswordCommand) {
        val tokenEntity = passwordResetTokenRepository.findByToken(command.token)
            ?: throw TokenInvalidException()

        if (!tokenEntity.isValid()) {
            throw TokenInvalidException()
        }

        val user = userRepository.findById(tokenEntity.userId)
            ?: throw DomainException("User not found")

        // Update password
        val updatedUser = user.withNewPassword(
            rawPassword = command.newPassword,
            passwordEncoder = { passwordEncoder.encode(it) }
        )
        userRepository.save(updatedUser)

        // Mark token as used
        passwordResetTokenRepository.save(tokenEntity.markUsed())
    }
}
