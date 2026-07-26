package com.sakena.facility.infrastructure.web.dto

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.model.BookingRules
import com.sakena.facility.domain.model.Facility
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Scheduling policy of a facility. Absent on a request means "keep the
 * defaults"; the domain re-validates every field it receives.
 */
data class BookingRulesPayload(
    val opensAt: LocalTime = BookingRules.DEFAULT.opensAt,
    val closesAt: LocalTime = BookingRules.DEFAULT.closesAt,
    val closedDays: Set<DayOfWeek> = emptySet(),

    @field:Min(value = 15, message = "minDurationMinutes must be at least 15")
    val minDurationMinutes: Int = BookingRules.DEFAULT.minDurationMinutes,

    @field:Min(value = 15, message = "maxDurationMinutes must be at least 15")
    val maxDurationMinutes: Int = BookingRules.DEFAULT.maxDurationMinutes,

    @field:Min(value = 1, message = "maxAdvanceDays must be at least 1")
    @field:Max(value = 365, message = "maxAdvanceDays must be at most 365")
    val maxAdvanceDays: Int = BookingRules.DEFAULT.maxAdvanceDays,

    /** 0 means unlimited. */
    @field:Min(value = 0, message = "maxPerResidentPerWeek must not be negative")
    val maxPerResidentPerWeek: Int = BookingRules.DEFAULT.maxPerResidentPerWeek,

    @field:DecimalMin(value = "0.0", message = "hourlyPrice must not be negative")
    val hourlyPrice: BigDecimal = BookingRules.DEFAULT.hourlyPrice,
) {
    fun toDomain() = BookingRules(
        opensAt = opensAt,
        closesAt = closesAt,
        closedDays = closedDays,
        minDurationMinutes = minDurationMinutes,
        maxDurationMinutes = maxDurationMinutes,
        maxAdvanceDays = maxAdvanceDays,
        maxPerResidentPerWeek = maxPerResidentPerWeek,
        hourlyPrice = hourlyPrice,
    )

    companion object {
        fun from(rules: BookingRules) = BookingRulesPayload(
            opensAt = rules.opensAt,
            closesAt = rules.closesAt,
            closedDays = rules.closedDays,
            minDurationMinutes = rules.minDurationMinutes,
            maxDurationMinutes = rules.maxDurationMinutes,
            maxAdvanceDays = rules.maxAdvanceDays,
            maxPerResidentPerWeek = rules.maxPerResidentPerWeek,
            hourlyPrice = rules.hourlyPrice,
        )
    }
}

data class CreateFacilityRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:Size(max = 50, message = "icon must be at most 50 characters")
    val icon: String? = null,

    @field:Min(value = 1, message = "capacity must be at least 1")
    @field:Max(value = 1000, message = "capacity must be at most 1000")
    val capacity: Int = Facility.DEFAULT_CAPACITY,

    @field:Valid
    val rules: BookingRulesPayload = BookingRulesPayload(),
) {
    fun toCommand() = CreateFacilityCommand(
        name = name,
        icon = icon,
        capacity = capacity,
        rules = rules.toDomain(),
    )
}

data class UpdateFacilityRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 150, message = "name must be at most 150 characters")
    val name: String,

    @field:Size(max = 50, message = "icon must be at most 50 characters")
    val icon: String? = null,

    @field:Min(value = 1, message = "capacity must be at least 1")
    @field:Max(value = 1000, message = "capacity must be at most 1000")
    val capacity: Int,

    @field:Valid
    val rules: BookingRulesPayload = BookingRulesPayload(),
) {
    fun toCommand() = UpdateFacilityCommand(
        name = name,
        icon = icon,
        capacity = capacity,
        rules = rules.toDomain(),
    )
}

data class FacilityResponse(
    val id: UUID,
    val name: String,
    val icon: String?,
    val capacity: Int,
    val rules: BookingRulesPayload,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(facility: Facility) = FacilityResponse(
            id = facility.id.value,
            name = facility.name,
            icon = facility.icon,
            capacity = facility.capacity,
            rules = BookingRulesPayload.from(facility.rules),
            createdAt = facility.createdAt,
            updatedAt = facility.updatedAt,
        )
    }
}
