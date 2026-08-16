package com.sakena.property.application

import com.sakena.property.application.command.CreateApartmentCommand
import com.sakena.property.application.command.UpdateApartmentCommand
import com.sakena.property.domain.ApartmentNotFoundException
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ApartmentService(
    private val apartmentRepository: ApartmentRepository,
    private val buildingRepository: BuildingRepository,
) {

    fun create(command: CreateApartmentCommand, managerId: UserId): Apartment {
        requireManagedBuilding(command.buildingId, managerId)
        val apartment = Apartment.create(
            buildingId = command.buildingId,
            unitNumber = command.unitNumber,
            floorNumber = command.floorNumber,
            areaSquareMeters = command.areaSquareMeters,
            bedrooms = command.bedrooms,
        )
        return apartmentRepository.save(apartment)
    }

    fun update(id: ApartmentId, command: UpdateApartmentCommand, managerId: UserId): Apartment {
        val apartment = requireApartment(id)
        requireManagedBuilding(apartment.buildingId, managerId)
        requireManagedBuilding(command.buildingId, managerId)
        apartment.updateDetails(
            newBuildingId = command.buildingId,
            newUnitNumber = command.unitNumber,
            newFloorNumber = command.floorNumber,
            newAreaSquareMeters = command.areaSquareMeters,
            newBedrooms = command.bedrooms,
        )
        return apartmentRepository.save(apartment)
    }

    fun delete(id: ApartmentId, managerId: UserId) {
        val apartment = requireApartment(id)
        requireManagedBuilding(apartment.buildingId, managerId)
        apartmentRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: ApartmentId, managerId: UserId): Apartment {
        val apartment = requireApartment(id)
        requireManagedBuilding(apartment.buildingId, managerId)
        return apartment
    }

    @Transactional(readOnly = true)
    fun getAll(buildingId: BuildingId?, managerId: UserId): List<Apartment> {
        val managedBuilding = buildingRepository.findByManagerId(managerId)
        if (managedBuilding == null) {
            if (buildingId != null) throw DomainForbiddenException("You do not manage this building")
            return emptyList()
        }
        if (buildingId != null && buildingId != managedBuilding.id) {
            throw DomainForbiddenException("You do not manage this building")
        }
        return apartmentRepository.findAllByBuildingId(managedBuilding.id)
    }

    private fun requireApartment(id: ApartmentId): Apartment =
        apartmentRepository.findById(id) ?: throw ApartmentNotFoundException(id)

    private fun requireManagedBuilding(id: BuildingId, managerId: UserId) {
        val building = buildingRepository.findById(id) ?: throw BuildingNotFoundException(id)
        if (building.managerId != managerId) {
            throw DomainForbiddenException("You do not manage this building")
        }
    }
}
