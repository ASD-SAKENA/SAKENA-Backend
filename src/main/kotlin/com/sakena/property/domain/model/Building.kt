package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.time.Instant

class Building private constructor(
    val id: BuildingId,
    name: String,
    address: String,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var name: String = name
        private set

    var address: String = address
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun updateDetails(newName: String, newAddress: String) {
        name = validateName(newName)
        address = validateAddress(newAddress)
        touch()
    }

    private fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_NAME_LENGTH = 150
        const val MAX_ADDRESS_LENGTH = 500

        fun create(name: String, address: String): Building {
            val now = Instant.now()
            return Building(
                id = BuildingId.new(),
                name = validateName(name),
                address = validateAddress(address),
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: BuildingId,
            name: String,
            address: String,
            createdAt: Instant,
            updatedAt: Instant,
        ): Building = Building(id, name, address, createdAt, updatedAt)

        private fun validateName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Building name must not be blank")
            if (trimmed.length > MAX_NAME_LENGTH) {
                throw DomainValidationException("Building name must be at most $MAX_NAME_LENGTH characters")
            }
            return trimmed
        }

        private fun validateAddress(address: String): String {
            val trimmed = address.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Building address must not be blank")
            if (trimmed.length > MAX_ADDRESS_LENGTH) {
                throw DomainValidationException("Building address must be at most $MAX_ADDRESS_LENGTH characters")
            }
            return trimmed
        }
    }
}
