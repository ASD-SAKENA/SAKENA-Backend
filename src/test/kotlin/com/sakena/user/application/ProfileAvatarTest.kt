package com.sakena.user.application

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.AvatarStorage
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileAvatarTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    private val avatarStorage = mockk<AvatarStorage>(relaxed = true)
    private val service = ProfileService(userRepository, passwordEncoder, avatarStorage)

    private fun user(avatarKey: String? = null) = User(
        id = UserId.generate(),
        username = "resident",
        email = "resident@example.com",
        passwordHash = "hash",
        role = Role.RESIDENT,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        avatarObjectKey = avatarKey,
    )

    private fun upload(
        contentType: String = "image/png",
        sizeBytes: Long = 1024,
    ) = AvatarUpload("me.png", contentType, sizeBytes, "x".byteInputStream())

    @Test
    fun `setting a picture stores it and keeps the returned key`() {
        val existing = user()
        every { userRepository.findById(existing.id) } returns existing
        every { userRepository.save(any()) } answers { firstArg() }
        every { avatarStorage.store(any(), any(), any(), any(), any()) } returns "avatars/key"

        val updated = service.setAvatar(existing.id, upload())

        assertEquals("avatars/key", updated.avatarObjectKey)
    }

    @Test
    fun `replacing a picture removes the previous one`() {
        // Otherwise every change leaves another orphaned object behind.
        val existing = user(avatarKey = "avatars/old")
        every { userRepository.findById(existing.id) } returns existing
        every { userRepository.save(any()) } answers { firstArg() }
        every { avatarStorage.store(any(), any(), any(), any(), any()) } returns "avatars/new"

        service.setAvatar(existing.id, upload())

        verify(exactly = 1) { avatarStorage.delete("avatars/old") }
    }

    @Test
    fun `removing a picture clears the key so the initial is shown again`() {
        val existing = user(avatarKey = "avatars/old")
        every { userRepository.findById(existing.id) } returns existing
        every { userRepository.save(any()) } answers { firstArg() }

        val updated = service.removeAvatar(existing.id)

        assertNull(updated.avatarObjectKey)
        verify(exactly = 1) { avatarStorage.delete("avatars/old") }
    }

    @Test
    fun `a user without a picture has no avatar url`() {
        assertNull(service.avatarUrl(user()))
    }

    @Test
    fun `an oversized image is refused`() {
        val existing = user()
        every { userRepository.findById(existing.id) } returns existing

        assertFailsWith<DomainValidationException> {
            service.setAvatar(
                existing.id,
                upload(sizeBytes = ProfileService.MAX_AVATAR_BYTES + 1),
            )
        }
        verify(exactly = 0) { avatarStorage.store(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a non-image upload is refused`() {
        val existing = user()
        every { userRepository.findById(existing.id) } returns existing

        assertFailsWith<DomainValidationException> {
            service.setAvatar(existing.id, upload(contentType = "application/pdf"))
        }
        verify(exactly = 0) { avatarStorage.store(any(), any(), any(), any(), any()) }
    }
}
