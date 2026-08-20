package com.sakena.user.application

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.StaffBuildingMembershipRepository
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only directory of service staff, for a manager assigning a service
 * request to a worker. Scoped to the building the manager administers: a
 * staff member reaches a building only through that building's own
 * invitation, so one manager never sees — or can assign work to — another
 * building's workers.
 */
@Service
@Transactional(readOnly = true)
class StaffDirectoryService(
    private val userRepository: UserRepository,
    private val staffMembershipRepository: StaffBuildingMembershipRepository,
) {
    fun getActiveStaff(requesterManagedBuildingId: BuildingId?): List<User> {
        val buildingId = requesterManagedBuildingId
            ?: throw DomainForbiddenException("You do not manage a building")
        val staffIds = staffMembershipRepository.findStaffIdsByBuilding(buildingId).toSet()
        if (staffIds.isEmpty()) return emptyList()
        return userRepository.findAllByIds(staffIds)
            .filter { it.role == Role.STAFF && it.active }
    }
}
