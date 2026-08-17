package com.sakena.property.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BuildingAccessService(
    private val userRepository: UserRepository,
    private val residencyRepository: ResidencyRepository,
    private val apartmentRepository: ApartmentRepository,
) : BuildingAccess {

    override fun managedBuildingId(managerId: UserId): BuildingId {
        val manager = userRepository.findById(managerId)
            ?: throw DomainForbiddenException("You are not assigned to a building")
        if (manager.role != Role.MANAGER) {
            throw DomainForbiddenException("You are not assigned to a building")
        }
        return manager.managedBuildingId
            ?: throw DomainForbiddenException("You are not assigned to a building")
    }

    override fun residentBuildingId(residentId: UserId): BuildingId {
        val residency = residencyRepository.findActiveByResident(residentId)
            ?: throw DomainForbiddenException("You are not a resident of a building")
        return apartmentRepository.findById(residency.apartmentId)?.buildingId
            ?: throw DomainForbiddenException("Your residency is not linked to a building")
    }

    override fun requireManagerAccess(buildingId: BuildingId, managerId: UserId) {
        if (managedBuildingId(managerId) != buildingId) {
            throw DomainForbiddenException("You do not manage this building")
        }
    }

    override fun requireResidentAccess(buildingId: BuildingId, residentId: UserId) {
        if (residentBuildingId(residentId) != buildingId) {
            throw DomainForbiddenException("You are not a resident of this building")
        }
    }

}
