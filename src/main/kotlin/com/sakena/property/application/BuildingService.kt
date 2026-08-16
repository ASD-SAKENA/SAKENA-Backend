package com.sakena.property.application

import com.sakena.property.application.command.CreateBuildingCommand
import com.sakena.property.application.command.UpdateBuildingCommand
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BuildingService(
    private val buildingRepository: BuildingRepository,
    private val apartmentRepository: ApartmentRepository,
) {

    fun create(command: CreateBuildingCommand, managerId: UserId): Building {
        if (buildingRepository.findByManagerId(managerId) != null) {
            throw DomainConflictException("A manager can only manage one building")
        }
        val building = Building.create(command.name, command.address, managerId)
        return buildingRepository.save(building)
    }

    fun update(id: BuildingId, command: UpdateBuildingCommand, managerId: UserId): Building {
        val building = requireManagedBuilding(id, managerId)
        building.updateDetails(command.name, command.address)
        return buildingRepository.save(building)
    }

    fun delete(id: BuildingId, managerId: UserId) {
        requireManagedBuilding(id, managerId)
        if (apartmentRepository.existsByBuildingId(id)) {
            throw DomainConflictException("Cannot delete building '$id' because it has apartments")
        }
        buildingRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: BuildingId, managerId: UserId): Building = requireManagedBuilding(id, managerId)

    @Transactional(readOnly = true)
    fun getAll(managerId: UserId): List<Building> = listOfNotNull(buildingRepository.findByManagerId(managerId))

    private fun requireManagedBuilding(id: BuildingId, managerId: UserId): Building {
        val building = buildingRepository.findById(id) ?: throw BuildingNotFoundException(id)
        if (building.managerId != managerId) {
            throw DomainForbiddenException("You do not manage this building")
        }
        return building
    }
}
