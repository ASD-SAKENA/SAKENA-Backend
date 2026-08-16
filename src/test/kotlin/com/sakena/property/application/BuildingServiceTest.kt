package com.sakena.property.application

import com.sakena.property.application.command.UpdateBuildingCommand
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildingServiceTest {

    private val buildingRepository = mockk<BuildingRepository>()
    private val service = BuildingService(buildingRepository)

    @Test
    fun `create persists a building`() {
        val saved = slot<Building>()
        every { buildingRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.create("Tower A", "Main Street")

        assertEquals("Tower A", result.name)
        assertEquals("Main Street", result.address)
        verify(exactly = 1) { buildingRepository.save(any()) }
    }

    @Test
    fun `getById throws when building is missing`() {
        val id = BuildingId.new()
        every { buildingRepository.findById(id) } returns null

        assertFailsWith<BuildingNotFoundException> { service.getById(id) }
    }

    @Test
    fun `update succeeds when the requester manages that building`() {
        val id = BuildingId.new()
        val building = Building.create("Old name", "Old address")
        every { buildingRepository.findById(id) } returns building
        every { buildingRepository.save(any()) } answers { firstArg() }

        val result = service.update(id, id, UpdateBuildingCommand("New name", "New address"))

        assertEquals("New name", result.name)
        assertEquals("New address", result.address)
    }

    @Test
    fun `update is rejected for a manager who administers a different building`() {
        val id = BuildingId.new()
        val requesterBuildingId = BuildingId.new()

        assertFailsWith<DomainForbiddenException> {
            service.update(id, requesterBuildingId, UpdateBuildingCommand("New name", "New address"))
        }
        verify(exactly = 0) { buildingRepository.save(any()) }
    }

    @Test
    fun `update is rejected when the requester manages no building at all`() {
        val id = BuildingId.new()

        assertFailsWith<DomainForbiddenException> {
            service.update(id, null, UpdateBuildingCommand("New name", "New address"))
        }
    }
}
