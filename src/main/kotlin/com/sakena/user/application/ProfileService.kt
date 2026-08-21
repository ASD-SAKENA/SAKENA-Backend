package com.sakena.user.application

import com.sakena.shared.domain.DomainException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.AvatarStorage
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
    private val passwordEncoder: PasswordEncoder,
    private val avatarStorage: AvatarStorage,
) {

    companion object {
        const val MAX_AVATAR_BYTES = 5L * 1024 * 1024
        private val ALLOWED_AVATAR_TYPES =
            setOf("image/jpeg", "image/png", "image/webp")
    }

    /**
     * Replaces the user's profile picture and returns the updated user.
     *
     * The previous image is deleted afterwards, and only once the new key is
     * safely persisted — a failed cleanup leaves an orphaned object, which is
     * cheap, whereas deleting first would lose the picture on a later failure.
     */
    fun setAvatar(userId: UserId, upload: AvatarUpload): User {
        val user = userRepository.findById(userId)
            ?: throw DomainException("User not found")
        validateAvatar(upload)

        val previousKey = user.avatarObjectKey
        val objectKey = avatarStorage.store(
            userId = userId,
            originalFilename = upload.originalFilename,
            contentType = upload.contentType,
            sizeBytes = upload.sizeBytes,
            content = upload.content,
        )
        val saved = userRepository.save(user.withAvatar(objectKey))
        previousKey?.let { runCatching { avatarStorage.delete(it) } }
        return saved
    }

    /** Removes the picture; the UI falls back to the user's initial again. */
    fun removeAvatar(userId: UserId): User {
        val user = userRepository.findById(userId)
            ?: throw DomainException("User not found")
        val saved = userRepository.save(user.withAvatar(null))
        user.avatarObjectKey?.let { runCatching { avatarStorage.delete(it) } }
        return saved
    }

    /** Avatars are private in storage, so the client gets a short-lived URL. */
    @Transactional(readOnly = true)
    fun avatarUrl(user: User): String? =
        user.avatarObjectKey?.let { avatarStorage.presignedUrl(it) }

    private fun validateAvatar(upload: AvatarUpload) {
        if (upload.sizeBytes <= 0) {
            throw DomainValidationException("The uploaded image is empty")
        }
        if (upload.sizeBytes > MAX_AVATAR_BYTES) {
            throw DomainValidationException("Profile picture must be at most 5 MB")
        }
        val baseType = upload.contentType.substringBefore(';').trim().lowercase()
        if (baseType !in ALLOWED_AVATAR_TYPES) {
            throw DomainValidationException("Unsupported image type '$baseType'")
        }
    }

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
