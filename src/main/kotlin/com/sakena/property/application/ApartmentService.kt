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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ApartmentService(
    private val apartmentRepository: ApartmentRepository,
    private val buildingRepository: BuildingRepository,
) {

    fun create(command: CreateApartmentCommand, requesterManagedBuildingId: BuildingId?): Apartment {
        requireOwnedBuilding(command.buildingId, requesterManagedBuildingId)
        val apartment = Apartment.create(
            buildingId = command.buildingId,
            unitNumber = command.unitNumber,
            floorNumber = command.floorNumber,
            areaSquareMeters = command.areaSquareMeters,
            bedrooms = command.bedrooms,
        )
        return apartmentRepository.save(apartment)
    }

    fun update(id: ApartmentId, command: UpdateApartmentCommand, requesterManagedBuildingId: BuildingId?): Apartment {
        val apartment = requireApartment(id)
        requireOwnedBuilding(apartment.buildingId, requesterManagedBuildingId)
        // A manager may never move a unit into a building they don't administer either.
        requireOwnedBuilding(command.buildingId, requesterManagedBuildingId)
        apartment.updateDetails(
            newBuildingId = command.buildingId,
            newUnitNumber = command.unitNumber,
            newFloorNumber = command.floorNumber,
            newAreaSquareMeters = command.areaSquareMeters,
            newBedrooms = command.bedrooms,
        )
        return apartmentRepository.save(apartment)
    }

    fun delete(id: ApartmentId, requesterManagedBuildingId: BuildingId?) {
        val apartment = requireApartment(id)
        requireOwnedBuilding(apartment.buildingId, requesterManagedBuildingId)
        apartmentRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: ApartmentId): Apartment = requireApartment(id)

    @Transactional(readOnly = true)
    fun getAll(buildingId: BuildingId?): List<Apartment> =
        if (buildingId == null) {
            apartmentRepository.findAll()
        } else {
            requireBuilding(buildingId)
            apartmentRepository.findAllByBuildingId(buildingId)
        }

    private fun requireApartment(id: ApartmentId): Apartment =
        apartmentRepository.findById(id) ?: throw ApartmentNotFoundException(id)

    private fun requireBuilding(id: BuildingId) {
        if (!buildingRepository.existsById(id)) throw BuildingNotFoundException(id)
    }

    private fun requireOwnedBuilding(id: BuildingId, requesterManagedBuildingId: BuildingId?) {
        if (requesterManagedBuildingId != id) {
            throw DomainForbiddenException("You do not manage building '$id'")
        }
        requireBuilding(id)
    }
}
