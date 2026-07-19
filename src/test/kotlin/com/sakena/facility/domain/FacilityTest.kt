package com.sakena.facility.domain

import com.sakena.facility.domain.model.Facility
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FacilityTest {

    @Test
    fun `create trims name and normalizes a blank icon to null`() {
        val facility = Facility.create("  Pool  ", "   ")

        assertEquals("Pool", facility.name)
        assertNull(facility.icon)
    }

    @Test
    fun `create rejects a blank name`() {
        assertFailsWith<DomainValidationException> {
            Facility.create("   ", null)
        }
    }

    @Test
    fun `create rejects an overlong name`() {
        assertFailsWith<DomainValidationException> {
            Facility.create("x".repeat(Facility.MAX_NAME_LENGTH + 1), null)
        }
    }

    @Test
    fun `update replaces name, icon and capacity`() {
        val facility = Facility.create("Pool", "pool")

        facility.update("Gym", "fitness_center", 15)

        assertEquals("Gym", facility.name)
        assertEquals("fitness_center", facility.icon)
        assertEquals(15, facility.capacity)
    }

    @Test
    fun `create defaults capacity and rejects a non-positive one`() {
        assertEquals(Facility.DEFAULT_CAPACITY, Facility.create("Pool", null).capacity)
        assertFailsWith<DomainValidationException> {
            Facility.create("Pool", null, capacity = 0)
        }
    }
}
