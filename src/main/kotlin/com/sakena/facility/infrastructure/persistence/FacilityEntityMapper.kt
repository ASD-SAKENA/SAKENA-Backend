package com.sakena.facility.infrastructure.persistence

import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId

/** Translates between the domain aggregate and its JPA representation. */
internal object FacilityEntityMapper {

    fun toEntity(facility: Facility): FacilityEntity =
        FacilityEntity(
            id = facility.id.value,
            name = facility.name,
            icon = facility.icon,
            createdAt = facility.createdAt,
            updatedAt = facility.updatedAt,
        )

    fun toDomain(entity: FacilityEntity): Facility =
        Facility.reconstitute(
            id = FacilityId(entity.id),
            name = entity.name,
            icon = entity.icon,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
