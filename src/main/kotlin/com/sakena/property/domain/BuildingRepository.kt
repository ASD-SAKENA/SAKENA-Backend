package com.sakena.property.domain

import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId

interface BuildingRepository {
    fun save(building: Building): Building

    fun findById(id: BuildingId): Building?

    fun findAll(): List<Building>

    fun existsById(id: BuildingId): Boolean

    fun deleteById(id: BuildingId)
}
