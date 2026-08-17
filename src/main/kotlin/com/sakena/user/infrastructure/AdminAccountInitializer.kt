package com.sakena.user.infrastructure

import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Provisions the single ADMIN account from trusted configuration at startup.
 * This is the only way an ADMIN user is ever created — public registration
 * rejects the role outright (see [com.sakena.user.application.AuthService.register])
 * so nobody can self-promote. Leaving the env vars unset skips seeding, e.g.
 * in tests or an environment that doesn't need an admin yet.
 */
@Component
class AdminAccountInitializer(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.admin.username:}") private val username: String,
    @Value("\${app.admin.email:}") private val email: String,
    @Value("\${app.admin.password:}") private val password: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminAccountInitializer::class.java)

    override fun run(args: ApplicationArguments) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            log.info("ADMIN_USERNAME/ADMIN_EMAIL/ADMIN_PASSWORD not set — skipping admin seed")
            return
        }
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            return
        }

        val admin = User.register(
            username = username,
            email = email,
            rawPassword = password,
            passwordEncoder = { passwordEncoder.encode(it) },
            role = Role.ADMIN,
        )
        userRepository.save(admin)
        log.info("Seeded the administrator account '{}'", username)
    }
}
