package com.sakena.property.domain.model

import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildingTest {

    @Test
    fun `create trims building fields`() {
        val managerId = UserId.generate()
        val building = Building.create("  North Tower  ", "  Main Street  ", managerId)

        assertEquals("North Tower", building.name)
        assertEquals("Main Street", building.address)
        assertEquals(managerId, building.managerId)
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
