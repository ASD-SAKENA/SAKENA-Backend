package com.sakena.facility.domain.model

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainValidationException
import java.time.Instant

class Facility private constructor(
    val id: FacilityId,
    /**
     * The building that owns this facility. Rows created before building
     * isolation was introduced may remain unassigned and are intentionally
     * invisible through building-scoped application use cases.
     */
    val buildingId: BuildingId?,
    name: String,
    icon: String?,
    capacity: Int,
    rules: BookingRules,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var name: String = name
        private set

    var icon: String? = icon
        private set

    var capacity: Int = capacity
        private set

    var rules: BookingRules = rules
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun update(
        newName: String,
        newIcon: String?,
        newCapacity: Int,
        newRules: BookingRules,
    ) {
        this.name = validateName(newName)
        this.icon = validateIcon(newIcon)
        this.capacity = validateCapacity(newCapacity)
        this.rules = newRules
        touch()
    }

    private fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_NAME_LENGTH = 150
        const val MAX_ICON_LENGTH = 50
        const val DEFAULT_CAPACITY = 10
        const val MAX_CAPACITY = 1_000

        fun create(
            buildingId: BuildingId,
            name: String,
            icon: String?,
            capacity: Int = DEFAULT_CAPACITY,
            rules: BookingRules = BookingRules.DEFAULT,
        ): Facility {
            val now = Instant.now()
            return Facility(
                id = FacilityId.new(),
                buildingId = buildingId,
                name = validateName(name),
                icon = validateIcon(icon),
                capacity = validateCapacity(capacity),
                rules = rules,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: FacilityId,
            buildingId: BuildingId?,
            name: String,
            icon: String?,
            capacity: Int,
            rules: BookingRules,
            createdAt: Instant,
            updatedAt: Instant,
        ): Facility = Facility(id, buildingId, name, icon, capacity, rules, createdAt, updatedAt)

        private fun validateName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Facility name must not be blank")
            if (trimmed.length > MAX_NAME_LENGTH) {
                throw DomainValidationException("Facility name must be at most $MAX_NAME_LENGTH characters")
            }
            return trimmed
        }

        private fun validateIcon(icon: String?): String? {
            val trimmed = icon?.trim()?.ifEmpty { null } ?: return null
            if (trimmed.length > MAX_ICON_LENGTH) {
                throw DomainValidationException("Facility icon must be at most $MAX_ICON_LENGTH characters")
            }
            return trimmed
        }

        private fun validateCapacity(capacity: Int): Int {
            if (capacity < 1) throw DomainValidationException("Facility capacity must be at least 1")
            if (capacity > MAX_CAPACITY) {
                throw DomainValidationException("Facility capacity must be at most $MAX_CAPACITY")
            }
            return capacity
        }
    }
}
