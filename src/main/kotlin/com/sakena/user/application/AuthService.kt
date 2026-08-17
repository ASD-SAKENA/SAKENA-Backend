package com.sakena.user.application

import com.sakena.property.application.BuildingService
import com.sakena.user.domain.PasswordResetToken
import com.sakena.user.domain.PasswordResetTokenRepository
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.exceptions.InactiveAccountException
import com.sakena.user.domain.exceptions.InvalidCredentialsException
import com.sakena.user.domain.exceptions.TokenInvalidException
import com.sakena.user.domain.exceptions.UserAlreadyExistsException
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.model.Wallet
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val buildingService: BuildingService,
    private val walletRepository: WalletRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailSender: EmailSender,
    @Value("\${app.frontend-url:http://localhost:3000}")
    private val frontendUrl: String,
    @Value("\${app.reset-token-expiration-minutes:60}")
    private var resetTokenExpMinutes: Long
) {

    /**
     * A manager administers exactly one building, created here and never
     * chosen by the registering user — letting a signup pick an existing
     * buildingId would let anyone declare themselves the manager of a
     * building they have no relationship to. The manager renames/re-addresses
     * it afterward via `PUT /buildings/{id}`.
     */
    fun register(command: RegisterCommand): User {
        if (userRepository.existsByUsername(command.username)) {
            throw UserAlreadyExistsException("username", command.username)
        }
        if (userRepository.existsByEmail(command.email)) {
            throw UserAlreadyExistsException("email", command.email)
        }

        val role = command.role?.let(Role::from) ?: Role.RESIDENT
        // The administrator account is provisioned once at startup from
        // trusted configuration (see AdminAccountInitializer) — public signup
        // must never be able to mint one, or anyone could self-promote.
        if (role == Role.ADMIN) {
            throw DomainValidationException("The administrator role cannot be self-registered")
        }

        val managedBuildingId = if (role == Role.MANAGER) {
            val building = buildingService.create(
                name = "ساختمان ${command.username}",
                address = "آدرس ثبت نشده",
            )
            walletRepository.save(Wallet.createBuilding(building.id))
            building.id
        } else {
            null
        }

        val user = User.register(
            username = command.username,
            email = command.email,
            rawPassword = command.password,
            passwordEncoder = { passwordEncoder.encode(it) },
            role = role,
            managedBuildingId = managedBuildingId
        )

        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username)

    /**
     * The login field is labelled "username" but a user who registered with
     * their email nearby often types it here out of habit — accept either so
     * that mistake doesn't lock them out of an otherwise-valid account.
     * Returns the resolved user alongside the token so the caller reports
     * their actual username/role, not whatever string they typed to log in.
     */
    fun login(command: LoginCommand): LoginResult {
        val user = userRepository.findByUsername(command.username)
            ?: userRepository.findByEmail(command.username.trim().lowercase())
            ?: throw InvalidCredentialsException()

        if (!user.verifyPassword(command.password, passwordEncoder::matches)) {
            throw InvalidCredentialsException()
        }

        if (!user.active) {
            throw InactiveAccountException()
        }

        val token = jwtTokenProvider.generateToken(user.username, user.role.name)
        return LoginResult(token, user)
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
            ?: throw TokenInvalidException()

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
