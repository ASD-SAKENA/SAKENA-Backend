package com.sakena.user.application

import com.sakena.shared.domain.DomainException
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.user.domain.exceptions.InvalidCredentialsException
import com.sakena.user.domain.exceptions.UserAlreadyExistsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProfileService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun getProfile(userId: UserId): User {
        return userRepository.findById(userId)
            ?: throw DomainException("User not found")
    }

    fun getUserByUsername(username: String): User? = userRepository.findByUsername(username)

    fun updateProfile(userId: UserId, command: UpdateProfileCommand): User {
        val user = userRepository.findById(userId)
            ?: throw DomainException("User not found")

        var updatedUser = user

        command.username?.let { newUsername ->
            if (newUsername != user.username && userRepository.existsByUsername(newUsername)) {
                throw UserAlreadyExistsException("username", newUsername)
            }
            updatedUser = updatedUser.copy(username = newUsername)
        }

        command.email?.let { newEmail ->
            if (newEmail != user.email && userRepository.existsByEmail(newEmail)) {
                throw UserAlreadyExistsException("email", newEmail)
            }
            updatedUser = updatedUser.copy(email = newEmail.lowercase())
        }

        return userRepository.save(updatedUser)
    }

    fun changePassword(userId: UserId, command: ChangePasswordCommand) {
        val user = userRepository.findById(userId)
            ?: throw DomainException("User not found")

        if (!passwordEncoder.matches(command.currentPassword, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val newPasswordHash = passwordEncoder.encode(command.newPassword)
        val updatedUser = user.copy(passwordHash = newPasswordHash)
        userRepository.save(updatedUser)
    }
}
