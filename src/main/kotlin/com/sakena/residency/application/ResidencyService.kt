package com.sakena.residency.application

import com.sakena.property.domain.ApartmentRepository
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
    private val userRepository: UserRepository,
) {

    fun start(apartmentId: ApartmentId, command: StartResidencyCommand): Residency {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw EntityNotFoundException("Apartment with id '$apartmentId' was not found")
        }
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
    fun endCurrent(apartmentId: ApartmentId): Residency {
        val residency = residencyRepository.findActiveByApartment(apartmentId)
            ?: throw EntityNotFoundException("Unit '$apartmentId' has no current resident")
        residency.end()
        return residencyRepository.save(residency)
    }

    @Transactional(readOnly = true)
    fun getCurrent(apartmentId: ApartmentId): Residency? =
        residencyRepository.findActiveByApartment(apartmentId)

    @Transactional(readOnly = true)
    fun getHistory(apartmentId: ApartmentId): List<Residency> =
        residencyRepository.findAllByApartment(apartmentId)

    /** Active residencies of one building, or of every building when [buildingId] is null. */
    @Transactional(readOnly = true)
    fun getActiveByBuilding(buildingId: BuildingId?): List<Residency> =
        if (buildingId != null) {
            residencyRepository.findActiveByBuilding(buildingId)
        } else {
            residencyRepository.findAllActive()
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
