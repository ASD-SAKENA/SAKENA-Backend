package com.sakena.user.application

import com.sakena.membership.domain.StaffBuildingMembershipRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserAdminService(
    private val userRepository: UserRepository,
    private val buildingAccess: BuildingAccess,
    private val residencyRepository: ResidencyRepository,
    private val staffMembershipRepository: StaffBuildingMembershipRepository,
) {

    @Transactional(readOnly = true)
    fun getUsers(managerId: UserId, role: Role? = null): List<User> {
        val users = userRepository.findAllByIds(managedUserIds(managerId))
        return role?.let { r -> users.filter { it.role == r } } ?: users
    }

    fun changeActiveStatus(userId: UserId, active: Boolean, managerId: UserId): User {
        requireManagedUser(userId, managerId)
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        val updated = if (active) user.activate() else user.deactivate()
        return userRepository.save(updated)
    }

    fun changeSpecialty(userId: UserId, specialty: String?, managerId: UserId): User {
        requireManagedUser(userId, managerId)
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        return userRepository.save(user.withSpecialty(specialty))
    }

    private fun requireManagedUser(userId: UserId, managerId: UserId) {
        if (userId !in managedUserIds(managerId)) {
            throw DomainForbiddenException("You may only manage users assigned to your building")
        }
    }

    private fun managedUserIds(managerId: UserId): Set<UserId> {
        val buildingId = buildingAccess.managedBuildingId(managerId)
        val residentIds = residencyRepository.findActiveByBuilding(buildingId).map { it.residentId }
        val staffIds = staffMembershipRepository.findAllByBuilding(buildingId).map { it.staffId }
        return (residentIds + staffIds + managerId).toSet()
    }
}
