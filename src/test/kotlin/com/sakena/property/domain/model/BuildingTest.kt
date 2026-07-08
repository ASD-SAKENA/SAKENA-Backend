package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildingTest {

    @Test
    fun `create trims building fields`() {
        val building = Building.create("  North Tower  ", "  Main Street  ")

        assertEquals("North Tower", building.name)
        assertEquals("Main Street", building.address)
    }

    @Test
    fun `create rejects blank name`() {
        assertFailsWith<DomainValidationException> { Building.create("   ", "Address") }
    }

    @Test
    fun `update changes fields and bumps updatedAt`() {
        val building = Building.create("Old", "Old address")
        val before = building.updatedAt

        building.updateDetails("New", "New address")

        assertEquals("New", building.name)
        assertEquals("New address", building.address)
        assertTrue(building.updatedAt >= before)
    }
}
