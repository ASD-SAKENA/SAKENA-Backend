package com.sakena.residency.domain

import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.ResidencyId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.UserId

/**
 * Outbound port for persisting residencies. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface ResidencyRepository {
    fun save(residency: Residency): Residency

    fun findById(id: ResidencyId): Residency?

    /** The residency currently occupying a unit, if any. */
    fun findActiveByApartment(apartmentId: ApartmentId): Residency?

    /** The unit a resident currently occupies, if any. */
    fun findActiveByResident(residentId: UserId): Residency?

    /** Occupancy history of a unit, newest first. */
    fun findAllByApartment(apartmentId: ApartmentId): List<Residency>

    /** Active residencies of every unit in a building — one query for the units table. */
    fun findActiveByBuilding(buildingId: BuildingId): List<Residency>

    /** Active residencies across every building — the unfiltered "all buildings" view. */
    fun findAllActive(): List<Residency>
}

class ResidencyNotFoundException(id: ResidencyId) :
    EntityNotFoundException("Residency with id '$id' was not found")
