package com.sakena.facility.domain

import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.model.BuildingId

interface FacilityRepository {
    fun save(facility: Facility): Facility

    fun findByIdAndBuildingId(id: FacilityId, buildingId: BuildingId): Facility?

    fun findAllByBuildingId(buildingId: BuildingId): List<Facility>

    fun existsByIdAndBuildingId(id: FacilityId, buildingId: BuildingId): Boolean

    fun deleteById(id: FacilityId)
}
