package com.sakena.property.domain

import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId

interface ApartmentRepository {
    fun save(apartment: Apartment): Apartment

    fun findById(id: ApartmentId): Apartment?

    fun findAll(): List<Apartment>

    fun findAllByBuildingId(buildingId: BuildingId): List<Apartment>

    fun existsById(id: ApartmentId): Boolean

    fun existsByBuildingId(buildingId: BuildingId): Boolean

    fun deleteById(id: ApartmentId)
}
