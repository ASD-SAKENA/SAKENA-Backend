package com.sakena.user.application

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
) {

    /** Usernames of the given users, resolved in a single pass. */
    fun usernamesByIds(ids: Set<UserId>): Map<UserId, String> {
        if (ids.isEmpty()) return emptyMap()
        return ids.mapNotNull { id -> userRepository.findById(id)?.let { id to it.username } }.toMap()
    }
}
