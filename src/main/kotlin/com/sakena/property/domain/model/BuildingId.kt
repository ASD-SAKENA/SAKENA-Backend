package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

@JvmInline
value class BuildingId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): BuildingId = BuildingId(UUID.randomUUID())

        fun from(raw: String): BuildingId =
            try {
                BuildingId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid building id")
            }
    }
}
