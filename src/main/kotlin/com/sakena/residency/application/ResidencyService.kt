package com.sakena.residency.application

import com.sakena.property.domain.ApartmentNotFoundException
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Application service linking residents to the units they occupy. Guards the
 * two occupancy rules — a unit has at most one current resident, and a
 * resident occupies at most one unit — before delegating to the aggregate.
 */
@Service
@Transactional
class ResidencyService(
    private val residencyRepository: ResidencyRepository,
    private val apartmentRepository: ApartmentRepository,
    private val buildingRepository: BuildingRepository,
    private val buildingAccess: BuildingAccess,
    private val userRepository: UserRepository,
) {

    fun start(
        apartmentId: ApartmentId,
        command: StartResidencyCommand,
        managerId: UserId,
    ): Residency {
        requireManagedApartment(apartmentId, managerId)
        return startResidency(apartmentId, command)
    }

    /**
     * Starts the residency created by accepting an invitation. The invitation
     * is the authorization boundary, but the apartment must still belong to
     * the building named by that invitation.
     */
    fun startFromInvitation(
        apartmentId: ApartmentId,
        buildingId: BuildingId,
        command: StartResidencyCommand,
    ): Residency {
        val apartment = requireApartment(apartmentId)
        if (apartment.buildingId != buildingId) {
            throw DomainConflictException("The invited apartment does not belong to the invited building")
        }
        return startResidency(apartmentId, command)
    }

    private fun startResidency(apartmentId: ApartmentId, command: StartResidencyCommand): Residency {
        val resident = userRepository.findById(command.residentId)
            ?: throw EntityNotFoundException("User with id '${command.residentId}' was not found")
        if (resident.role == Role.STAFF) {
            throw DomainConflictException("Service staff cannot be registered as a unit resident")
        }
        residencyRepository.findActiveByApartment(apartmentId)?.let {
            throw DomainConflictException("This unit already has a current resident")
        }
        residencyRepository.findActiveByResident(command.residentId)?.let {
            throw DomainConflictException("This resident already occupies another unit")
        }

        return residencyRepository.save(
            Residency.start(apartmentId, command.residentId, command.tenancy),
        )
    }

    /** Moves the current resident out, leaving the unit vacant. */
    fun endCurrent(apartmentId: ApartmentId, managerId: UserId): Residency {
        requireManagedApartment(apartmentId, managerId)
        val residency = residencyRepository.findActiveByApartment(apartmentId)
            ?: throw EntityNotFoundException("Unit '$apartmentId' has no current resident")
        residency.end()
        return residencyRepository.save(residency)
    }

    @Transactional(readOnly = true)
    fun getCurrent(apartmentId: ApartmentId): Residency? =
        residencyRepository.findActiveByApartment(apartmentId)

    @Transactional(readOnly = true)
    fun getHistory(apartmentId: ApartmentId, managerId: UserId): List<Residency> {
        requireManagedApartment(apartmentId, managerId)
        return residencyRepository.findAllByApartment(apartmentId)
    }

    /** Active residencies of the signed-in manager's building. */
    @Transactional(readOnly = true)
    fun getActiveByBuilding(buildingId: BuildingId?, managerId: UserId): List<Residency> {
        val scopedBuildingId = if (buildingId != null) {
            buildingAccess.requireManagerAccess(buildingId, managerId)
            buildingId
        } else {
            buildingAccess.managedBuildingId(managerId)
        }
        return residencyRepository.findActiveByBuilding(scopedBuildingId)
    }

    /** The unit the signed-in resident occupies, or null while none is assigned. */
    @Transactional(readOnly = true)
    fun getMyResidency(residentId: UserId): ResidencyDetails? =
        residencyRepository.findActiveByResident(residentId)?.let(::describe)

    /**
     * Enriches a residency with the unit details and building name, so clients
     * can show "unit 12 — Niloufar tower" instead of raw identifiers.
     */
    @Transactional(readOnly = true)
    fun describe(residency: Residency): ResidencyDetails {
        val apartment = apartmentRepository.findById(residency.apartmentId)
        val building = apartment?.let { buildingRepository.findById(it.buildingId) }
        return ResidencyDetails(
            residency = residency,
            unitNumber = apartment?.unitNumber,
            buildingId = apartment?.buildingId,
            buildingName = building?.name,
            floorNumber = apartment?.floorNumber,
            areaSquareMeters = apartment?.areaSquareMeters,
            bedrooms = apartment?.bedrooms,
        )
    }

    private fun requireApartment(id: ApartmentId) =
        apartmentRepository.findById(id) ?: throw ApartmentNotFoundException(id)

    private fun requireManagedApartment(id: ApartmentId, managerId: UserId) {
        val apartment = requireApartment(id)
        buildingAccess.requireManagerAccess(apartment.buildingId, managerId)
    }
}

/** A residency together with the human-readable property it points at. */
data class ResidencyDetails(
    val residency: Residency,
    val unitNumber: String?,
    val buildingId: BuildingId?,
    val buildingName: String?,
    val floorNumber: Int?,
    val areaSquareMeters: BigDecimal?,
    val bedrooms: Int?,
)
