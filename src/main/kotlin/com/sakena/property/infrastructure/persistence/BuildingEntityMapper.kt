package com.sakena.property.infrastructure.persistence

import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId

internal object BuildingEntityMapper {

    fun toEntity(building: Building): BuildingEntity =
        BuildingEntity(
            id = building.id.value,
            name = building.name,
            address = building.address,
            createdAt = building.createdAt,
            updatedAt = building.updatedAt,
        )

    fun toDomain(entity: BuildingEntity): Building =
        Building.reconstitute(
            id = BuildingId(entity.id),
            name = entity.name,
            address = entity.address,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
