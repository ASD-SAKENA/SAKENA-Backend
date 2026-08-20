package com.sakena.residency.application

import com.sakena.notification.application.NotificationService
import com.sakena.notification.domain.model.NotificationType
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
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
    private val notificationService: NotificationService,
) {

    fun start(
        apartmentId: ApartmentId,
        command: StartResidencyCommand,
        requesterManagedBuildingId: BuildingId?,
    ): Residency {
        val apartment = apartmentRepository.findById(apartmentId)
            ?: throw EntityNotFoundException("Apartment with id '$apartmentId' was not found")
        requireOwnedBuilding(apartment.buildingId, requesterManagedBuildingId)
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

        val residency = residencyRepository.save(
            Residency.start(apartmentId, command.residentId, command.tenancy),
        )
        val buildingName = buildingRepository.findById(apartment.buildingId)?.name ?: "ساختمان"
        notificationService.notifyUser(
            recipientId = command.residentId,
            title = "تخصیص واحد",
            body = "واحد ${apartment.unitNumber} در «$buildingName» به شما اختصاص داده شد.",
            type = NotificationType.SYSTEM,
            href = "/dashboard",
        )
        return residency
    }

    /** Moves the current resident out, leaving the unit vacant. */
    fun endCurrent(apartmentId: ApartmentId, requesterManagedBuildingId: BuildingId?): Residency {
        val apartment = apartmentRepository.findById(apartmentId)
            ?: throw EntityNotFoundException("Apartment with id '$apartmentId' was not found")
        requireOwnedBuilding(apartment.buildingId, requesterManagedBuildingId)
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

    /**
     * Active residencies for the units table.
     *
     * - Manager with a managed building: an explicit [buildingId] must match it;
     *   omitting it defaults to that building (never another manager's data).
     * - Caller without a managed building (e.g. transitional data): an explicit
     *   filter scopes to one building; omitting it returns every active residency
     *   so the "all buildings" screen can show occupancy.
     */
    @Transactional(readOnly = true)
    fun getActiveByBuilding(buildingId: BuildingId?, requesterManagedBuildingId: BuildingId?): List<Residency> {
        if (requesterManagedBuildingId != null) {
            val target = buildingId ?: requesterManagedBuildingId
            requireOwnedBuilding(target, requesterManagedBuildingId)
            return residencyRepository.findActiveByBuilding(target)
        }
        return if (buildingId != null) {
            if (!buildingRepository.existsById(buildingId)) {
                throw EntityNotFoundException("Building with id '$buildingId' was not found")
            }
            residencyRepository.findActiveByBuilding(buildingId)
        } else {
            residencyRepository.findAllActive()
        }
    }

    /** The unit the signed-in resident occupies, or null while none is assigned. */
    @Transactional(readOnly = true)
    fun getMyResidency(residentId: UserId): ResidencyDetails? =
        residencyRepository.findActiveByResident(residentId)?.let(::describe)

    /**
     * Guards resident actions (voting, booking, filing a request) that only
     * make sense once a resident actually occupies a unit — a person who
     * hasn't been moved into an apartment yet isn't a building member.
     */
    @Transactional(readOnly = true)
    fun requireActiveResidency(residentId: UserId): Residency =
        residencyRepository.findActiveByResident(residentId)
            ?: throw DomainForbiddenException("You must be an active resident of a unit to do this")

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

    private fun requireOwnedBuilding(id: BuildingId, requesterManagedBuildingId: BuildingId?) {
        if (requesterManagedBuildingId != id) {
            throw DomainForbiddenException("You do not manage building '$id'")
        }
        if (!buildingRepository.existsById(id)) {
            throw EntityNotFoundException("Building with id '$id' was not found")
        }
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
