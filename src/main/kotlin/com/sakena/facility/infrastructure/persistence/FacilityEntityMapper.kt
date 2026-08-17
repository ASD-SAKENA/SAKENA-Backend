package com.sakena.facility.infrastructure.persistence

import com.sakena.facility.domain.model.BookingRules
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.model.BuildingId
import java.time.DayOfWeek

/** Translates between the domain aggregate and its JPA representation. */
internal object FacilityEntityMapper {

    fun toEntity(facility: Facility): FacilityEntity =
        FacilityEntity(
            id = facility.id.value,
            buildingId = facility.buildingId?.value,
            name = facility.name,
            icon = facility.icon,
            capacity = facility.capacity,
            opensAt = facility.rules.opensAt,
            closesAt = facility.rules.closesAt,
            closedDays = facility.rules.closedDays.joinToString(",") { it.name },
            minDurationMinutes = facility.rules.minDurationMinutes,
            maxDurationMinutes = facility.rules.maxDurationMinutes,
            maxAdvanceDays = facility.rules.maxAdvanceDays,
            maxPerResidentPerWeek = facility.rules.maxPerResidentPerWeek,
            hourlyPrice = facility.rules.hourlyPrice,
            createdAt = facility.createdAt,
            updatedAt = facility.updatedAt,
        )

    fun toDomain(entity: FacilityEntity): Facility =
        Facility.reconstitute(
            id = FacilityId(entity.id),
            buildingId = entity.buildingId?.let(::BuildingId),
            name = entity.name,
            icon = entity.icon,
            capacity = entity.capacity,
            rules = BookingRules(
                opensAt = entity.opensAt,
                closesAt = entity.closesAt,
                closedDays = entity.closedDays
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map(DayOfWeek::valueOf)
                    .toSet(),
                minDurationMinutes = entity.minDurationMinutes,
                maxDurationMinutes = entity.maxDurationMinutes,
                maxAdvanceDays = entity.maxAdvanceDays,
                maxPerResidentPerWeek = entity.maxPerResidentPerWeek,
                hourlyPrice = entity.hourlyPrice,
            ),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
