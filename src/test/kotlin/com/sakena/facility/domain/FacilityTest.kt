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
    fun `update replaces name and icon`() {
        val facility = Facility.create("Pool", "pool")

        facility.update("Gym", "fitness_center")

        assertEquals("Gym", facility.name)
        assertEquals("fitness_center", facility.icon)
    }
}
