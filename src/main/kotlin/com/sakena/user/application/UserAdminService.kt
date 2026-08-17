package com.sakena.user.application

import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Administrator-only user management: every call here assumes an ADMIN caller (enforced at the controller). */
@Service
@Transactional
class UserAdminService(
    private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun getUsers(role: Role? = null): List<User> {
        val users = userRepository.findAll()
        return role?.let { r -> users.filter { it.role == r } } ?: users
    }

    fun changeActiveStatus(userId: UserId, active: Boolean): User {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        val updated = if (active) user.activate() else user.deactivate()
        return userRepository.save(updated)
    }

    fun changeSpecialty(userId: UserId, specialty: String?): User {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        return userRepository.save(user.withSpecialty(specialty))
    }

    /**
     * Reassigns a user's role. MANAGER is excluded here because promoting to
     * it must also provision a building and its wallet — that only happens
     * through registration today. Demoting an existing manager is excluded
     * for the same reason: it would orphan the building they administer.
     */
    fun changeRole(userId: UserId, role: Role): User {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        if (role == Role.MANAGER || user.role == Role.MANAGER) {
            throw DomainValidationException("Manager role changes are not supported through this endpoint")
        }
        return userRepository.save(user.withRole(role, managedBuildingId = null))
    }
}
