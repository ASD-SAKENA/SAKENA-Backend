package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.math.BigDecimal
import java.time.Instant

class Apartment private constructor(
    val id: ApartmentId,
    buildingId: BuildingId,
    unitNumber: String,
    floorNumber: Int,
    areaSquareMeters: BigDecimal,
    bedrooms: Int,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var buildingId: BuildingId = buildingId
        private set

    var unitNumber: String = unitNumber
        private set

    var floorNumber: Int = floorNumber
        private set

    var areaSquareMeters: BigDecimal = areaSquareMeters
        private set

    var bedrooms: Int = bedrooms
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun updateDetails(
        newBuildingId: BuildingId,
        newUnitNumber: String,
        newFloorNumber: Int,
        newAreaSquareMeters: BigDecimal,
        newBedrooms: Int,
    ) {
        buildingId = newBuildingId
        unitNumber = validateUnitNumber(newUnitNumber)
        floorNumber = validateFloorNumber(newFloorNumber)
        areaSquareMeters = validateArea(newAreaSquareMeters)
        bedrooms = validateBedrooms(newBedrooms)
        touch()
    }

    private fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_UNIT_NUMBER_LENGTH = 50
        const val MAX_BEDROOMS = 20

        fun create(
            buildingId: BuildingId,
            unitNumber: String,
            floorNumber: Int,
            areaSquareMeters: BigDecimal,
            bedrooms: Int,
        ): Apartment {
            val now = Instant.now()
            return Apartment(
                id = ApartmentId.new(),
                buildingId = buildingId,
                unitNumber = validateUnitNumber(unitNumber),
                floorNumber = validateFloorNumber(floorNumber),
                areaSquareMeters = validateArea(areaSquareMeters),
                bedrooms = validateBedrooms(bedrooms),
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: ApartmentId,
            buildingId: BuildingId,
            unitNumber: String,
            floorNumber: Int,
            areaSquareMeters: BigDecimal,
            bedrooms: Int,
            createdAt: Instant,
            updatedAt: Instant,
        ): Apartment = Apartment(
            id,
            buildingId,
            unitNumber,
            floorNumber,
            areaSquareMeters,
            bedrooms,
            createdAt,
            updatedAt,
        )

        private fun validateUnitNumber(unitNumber: String): String {
            val trimmed = unitNumber.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Apartment unit number must not be blank")
            if (trimmed.length > MAX_UNIT_NUMBER_LENGTH) {
                throw DomainValidationException("Apartment unit number must be at most $MAX_UNIT_NUMBER_LENGTH characters")
            }
            return trimmed
        }

        private fun validateFloorNumber(floorNumber: Int): Int {
            if (floorNumber < 0) throw DomainValidationException("Apartment floor number must not be negative")
            return floorNumber
        }

        private fun validateArea(areaSquareMeters: BigDecimal): BigDecimal {
            if (areaSquareMeters <= BigDecimal.ZERO) {
                throw DomainValidationException("Apartment area must be greater than zero")
            }
            return areaSquareMeters.stripTrailingZeros()
        }

        private fun validateBedrooms(bedrooms: Int): Int {
            if (bedrooms < 0) throw DomainValidationException("Apartment bedrooms must not be negative")
            if (bedrooms > MAX_BEDROOMS) {
                throw DomainValidationException("Apartment bedrooms must be at most $MAX_BEDROOMS")
            }
            return bedrooms
        }
    }
}
