package com.sakena.facility.application

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
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
) {

    fun create(command: CreateFacilityCommand): Facility {
        val facility = Facility.create(command.name, command.icon)
        return facilityRepository.save(facility)
    }

    fun update(id: FacilityId, command: UpdateFacilityCommand): Facility {
        val facility = requireFacility(id)
        facility.update(command.name, command.icon)
        return facilityRepository.save(facility)
    }

    fun delete(id: FacilityId) {
        if (!facilityRepository.existsById(id)) throw FacilityNotFoundException(id)
        facilityRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun getById(id: FacilityId): Facility = requireFacility(id)

    @Transactional(readOnly = true)
    fun getAll(): List<Facility> = facilityRepository.findAll()

    private fun requireFacility(id: FacilityId): Facility =
        facilityRepository.findById(id) ?: throw FacilityNotFoundException(id)
}
