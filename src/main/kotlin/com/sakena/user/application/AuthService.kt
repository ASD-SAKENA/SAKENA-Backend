package com.sakena.user.application

import com.sakena.shared.domain.DomainException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import com.sakena.user.domain.exceptions.InvalidCredentialsException
import com.sakena.user.domain.exceptions.UserAlreadyExistsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
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
}
