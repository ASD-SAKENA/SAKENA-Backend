package com.sakena.facility.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

@JvmInline
value class FacilityId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): FacilityId = FacilityId(UUID.randomUUID())

        fun from(raw: String): FacilityId =
            try {
                FacilityId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid facility id")
            }
    }
}
