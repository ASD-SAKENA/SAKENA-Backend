package com.sakena.user.application

import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only directory of service staff, for a manager assigning a service
 * request to a worker.
 *
 * Staff are a system-wide pool on purpose: they are contractor accounts that
 * take work from several buildings and belong to none of them, so every
 * manager sees the same list. Only staff appear here, unlike the admin-only
 * [UserAdminService] which sees every account.
 */
@Service
@Transactional(readOnly = true)
class StaffDirectoryService(
    private val userRepository: UserRepository,
) {
    fun getActiveStaff(): List<User> =
        userRepository.findAll().filter { it.role == Role.STAFF && it.active }
}
