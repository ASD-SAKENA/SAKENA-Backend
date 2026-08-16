package com.sakena.property.domain

import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId

interface BuildingRepository {
    fun save(building: Building): Building

    fun findById(id: BuildingId): Building?

    fun findByManagerId(managerId: UserId): Building?

    fun findAll(): List<Building>

    fun existsById(id: BuildingId): Boolean

    fun existsByIdAndManagerId(id: BuildingId, managerId: UserId): Boolean

    fun deleteById(id: BuildingId)
}
