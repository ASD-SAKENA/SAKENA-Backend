package com.sakena.facility.domain

import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId

/**
 * Outbound port for persisting facilities. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface FacilityRepository {
    fun save(facility: Facility): Facility

    fun findById(id: FacilityId): Facility?

    fun findAll(): List<Facility>

    fun existsById(id: FacilityId): Boolean

    fun deleteById(id: FacilityId)
}
