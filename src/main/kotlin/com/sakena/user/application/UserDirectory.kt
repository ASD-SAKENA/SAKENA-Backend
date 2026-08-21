package com.sakena.user.application

import com.sakena.user.domain.AvatarStorage
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only lookups of user display data for other bounded contexts, so they
 * never reach into the user aggregate or its repository directly.
 */
@Service
@Transactional(readOnly = true)
class UserDirectory(
    private val userRepository: UserRepository,
    private val avatarStorage: AvatarStorage,
) {

    /** Usernames of the given users, resolved in a single pass. */
    fun usernamesByIds(ids: Set<UserId>): Map<UserId, String> {
        if (ids.isEmpty()) return emptyMap()
        return ids.mapNotNull { id -> userRepository.findById(id)?.let { id to it.username } }.toMap()
    }

    /**
     * Avatar URLs of the given users, for those who have set one. Users
     * without a picture are absent from the map rather than mapped to null,
     * so the caller falls back to their initial.
     */
    fun avatarUrlsByIds(ids: Set<UserId>): Map<UserId, String> {
        if (ids.isEmpty()) return emptyMap()
        return ids.mapNotNull { id ->
            userRepository.findById(id)?.avatarObjectKey?.let { key ->
                // A storage hiccup must not take the whole chat page down:
                // a missing URL just means the initial is shown instead.
                runCatching { id to avatarStorage.presignedUrl(key) }.getOrNull()
            }
        }.toMap()
    }
}
