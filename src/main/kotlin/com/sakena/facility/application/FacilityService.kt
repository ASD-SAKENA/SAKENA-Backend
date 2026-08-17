package com.sakena.facility.application

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service orchestrating the Facility use cases. It owns
 * transaction boundaries and delegates all business rules to the [Facility]
 * aggregate, depending only on the domain port.
 */
@Service
@Transactional
class FacilityService(
    private val facilityRepository: FacilityRepository,
    private val buildingAccess: BuildingAccess,
) {

    fun create(command: CreateFacilityCommand, requestedBy: User): Facility {
        val buildingId = managedBuildingId(requestedBy)
        val facility = Facility.create(
            buildingId,
            command.name,
            command.icon,
            command.capacity,
            command.rules,
        )
        return facilityRepository.save(facility)
    }

    fun update(id: FacilityId, command: UpdateFacilityCommand, requestedBy: User): Facility {
        val facility = requireFacility(id, managedBuildingId(requestedBy))
        facility.update(command.name, command.icon, command.capacity, command.rules)
        return facilityRepository.save(facility)
    }

    fun delete(id: FacilityId, requestedBy: User) {
        val buildingId = managedBuildingId(requestedBy)
        if (!facilityRepository.existsByIdAndBuildingId(id, buildingId)) {
            throw FacilityNotFoundException(id)
        }
        facilityRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: FacilityId, requestedBy: User): Facility =
        requireFacility(id, accessibleBuildingId(requestedBy))

    @Transactional(readOnly = true)
    fun getAll(requestedBy: User): List<Facility> =
        facilityRepository.findAllByBuildingId(accessibleBuildingId(requestedBy))

    private fun requireFacility(id: FacilityId, buildingId: BuildingId): Facility =
        facilityRepository.findByIdAndBuildingId(id, buildingId) ?: throw FacilityNotFoundException(id)

    private fun accessibleBuildingId(requestedBy: User): BuildingId =
        when (requestedBy.role) {
            Role.MANAGER -> buildingAccess.managedBuildingId(requestedBy.id)
            Role.RESIDENT -> buildingAccess.residentBuildingId(requestedBy.id)
            Role.STAFF, Role.ADMIN -> throw DomainForbiddenException("You cannot access building facilities")
        }

    private fun managedBuildingId(requestedBy: User): BuildingId {
        if (requestedBy.role != Role.MANAGER) {
            throw DomainForbiddenException("Only a building manager can manage facilities")
        }
        return buildingAccess.managedBuildingId(requestedBy.id)
    }
}
