package com.sakena.user.application

import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.BuildingId
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
    private val userRepository: UserRepository,
    private val buildingRepository: BuildingRepository,
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
     * Reassigns a user's role. The superuser is the only one who can hand out
     * or revoke MANAGER, since doing so decides who administers a building —
     * a building can end up with several managers or none, both intentional
     * (e.g. temporarily covering for an absent manager, or freeing a building
     * to be reassigned later).
     */
    fun changeRole(userId: UserId, role: Role, managedBuildingId: BuildingId?): User {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        if (role == Role.MANAGER) {
            val buildingId = managedBuildingId
                ?: throw DomainValidationException("managedBuildingId is required when assigning the MANAGER role")
            if (!buildingRepository.existsById(buildingId)) {
                throw EntityNotFoundException("Building not found: ${buildingId.value}")
            }
            return userRepository.save(user.withRole(role, managedBuildingId = buildingId))
        }
        return userRepository.save(user.withRole(role, managedBuildingId = null))
    }
}
