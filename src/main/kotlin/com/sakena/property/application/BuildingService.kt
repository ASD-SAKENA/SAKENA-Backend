package com.sakena.property.application

import com.sakena.property.application.command.CreateBuildingCommand
import com.sakena.property.application.command.UpdateBuildingCommand
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BuildingService(
    private val buildingRepository: BuildingRepository,
    private val apartmentRepository: ApartmentRepository,
) {

    fun create(command: CreateBuildingCommand): Building {
        val building = Building.create(command.name, command.address)
        return buildingRepository.save(building)
    }

    fun update(id: BuildingId, command: UpdateBuildingCommand): Building {
        val building = requireBuilding(id)
        building.updateDetails(command.name, command.address)
        return buildingRepository.save(building)
    }

    fun delete(id: BuildingId) {
        if (!buildingRepository.existsById(id)) throw BuildingNotFoundException(id)
        if (apartmentRepository.existsByBuildingId(id)) {
            throw DomainConflictException("Cannot delete building '$id' because it has apartments")
        }
        buildingRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: BuildingId): Building = requireBuilding(id)

    @Transactional(readOnly = true)
    fun getAll(): List<Building> = buildingRepository.findAll()

    private fun requireBuilding(id: BuildingId): Building =
        buildingRepository.findById(id) ?: throw BuildingNotFoundException(id)
}
