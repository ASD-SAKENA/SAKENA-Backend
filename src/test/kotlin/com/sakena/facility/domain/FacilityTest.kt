package com.sakena.facility.domain

import com.sakena.facility.domain.model.BookingRules
import com.sakena.facility.domain.model.Facility
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FacilityTest {

    private val buildingId = BuildingId.new()

    @Test
    fun `create trims name and normalizes a blank icon to null`() {
        val facility = Facility.create(buildingId, "  Pool  ", "   ")

        assertEquals(buildingId, facility.buildingId)
        assertEquals("Pool", facility.name)
        assertNull(facility.icon)
    }

    @Test
    fun `create rejects a blank name`() {
        assertFailsWith<DomainValidationException> {
            Facility.create(buildingId, "   ", null)
        }
    }

    @Test
    fun `create rejects an overlong name`() {
        assertFailsWith<DomainValidationException> {
            Facility.create(buildingId, "x".repeat(Facility.MAX_NAME_LENGTH + 1), null)
        }
    }

    @Test
    fun `update replaces name, icon, capacity and booking rules`() {
        val facility = Facility.create(buildingId, "Pool", "pool")
        val newRules = BookingRules.DEFAULT.copy(hourlyPrice = BigDecimal("120000"))

        facility.update("Gym", "fitness_center", 15, newRules)

        assertEquals("Gym", facility.name)
        assertEquals("fitness_center", facility.icon)
        assertEquals(15, facility.capacity)
        assertEquals(newRules, facility.rules)
    }

    @Test
    fun `create defaults capacity and rejects a non-positive one`() {
        assertEquals(Facility.DEFAULT_CAPACITY, Facility.create(buildingId, "Pool", null).capacity)
        assertFailsWith<DomainValidationException> {
            Facility.create(buildingId, "Pool", null, capacity = 0)
        }
    }
}
