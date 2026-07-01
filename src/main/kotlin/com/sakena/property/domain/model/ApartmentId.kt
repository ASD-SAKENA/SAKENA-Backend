package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

@JvmInline
value class ApartmentId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ApartmentId = ApartmentId(UUID.randomUUID())

        fun from(raw: String): ApartmentId =
            try {
                ApartmentId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid apartment id")
            }
    }
}
