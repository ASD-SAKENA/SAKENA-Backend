package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApartmentTest {

    @Test
    fun `create trims unit number`() {
        val buildingId = BuildingId.new()
        val apartment = Apartment.create(buildingId, "  12A  ", 3, BigDecimal("85.50"), 2)

        assertEquals(buildingId, apartment.buildingId)
        assertEquals("12A", apartment.unitNumber)
        assertEquals(3, apartment.floorNumber)
        assertEquals(BigDecimal("85.5"), apartment.areaSquareMeters)
        assertEquals(2, apartment.bedrooms)
    }

    @Test
    fun `create rejects non-positive area`() {
        assertFailsWith<DomainValidationException> {
            Apartment.create(BuildingId.new(), "1", 0, BigDecimal.ZERO, 1)
        }
    }

    @Test
    fun `create rejects negative floor`() {
        assertFailsWith<DomainValidationException> {
            Apartment.create(BuildingId.new(), "1", -1, BigDecimal("40"), 1)
        }
    }
}
