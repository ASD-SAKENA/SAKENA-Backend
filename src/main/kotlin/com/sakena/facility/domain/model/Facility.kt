package com.sakena.facility.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.time.Instant

/**
 * Facility aggregate root — a reservable shared amenity of the building
 * (pool, gym, meeting hall, …) managed by the building manager. State
 * changes go through behaviour methods, never raw setters.
 */
class Facility private constructor(
    val id: FacilityId,
    name: String,
    icon: String?,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var name: String = name
        private set

    var icon: String? = icon
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun update(newName: String, newIcon: String?) {
        this.name = validateName(newName)
        this.icon = validateIcon(newIcon)
        touch()
    }

    private fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_NAME_LENGTH = 150
        const val MAX_ICON_LENGTH = 50

        fun create(name: String, icon: String?): Facility {
            val now = Instant.now()
            return Facility(
                id = FacilityId.new(),
                name = validateName(name),
                icon = validateIcon(icon),
                createdAt = now,
                updatedAt = now,
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: FacilityId,
            name: String,
            icon: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): Facility = Facility(id, name, icon, createdAt, updatedAt)

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
    }
}
