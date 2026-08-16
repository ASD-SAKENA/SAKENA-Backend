package com.sakena.property.application

import com.sakena.property.application.command.UpdateBuildingCommand
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BuildingService(
    private val buildingRepository: BuildingRepository,
) {

    /**
     * A building is created once, automatically, when its manager registers
     * ([com.sakena.user.application.AuthService.register]) — never picked by
     * an already-registered manager, or any manager could "adopt" a building
     * that isn't theirs. There is intentionally no manager-facing create
     * endpoint for this reason.
     */
    fun create(name: String, address: String): Building {
        val building = Building.create(name, address)
        return buildingRepository.save(building)
    }

    fun update(id: BuildingId, requesterManagedBuildingId: BuildingId?, command: UpdateBuildingCommand): Building {
        val building = requireOwnedBuilding(id, requesterManagedBuildingId)
        building.updateDetails(command.name, command.address)
        return buildingRepository.save(building)
    }

    @Transactional(readOnly = true)
    fun getById(id: BuildingId): Building = requireBuilding(id)

    @Transactional(readOnly = true)
    fun getAll(): List<Building> = buildingRepository.findAll()

    private fun requireBuilding(id: BuildingId): Building =
        buildingRepository.findById(id) ?: throw BuildingNotFoundException(id)

    private fun requireOwnedBuilding(id: BuildingId, requesterManagedBuildingId: BuildingId?): Building {
        if (requesterManagedBuildingId != id) {
            throw DomainForbiddenException("You do not manage building '$id'")
        }
        return requireBuilding(id)
    }
}
