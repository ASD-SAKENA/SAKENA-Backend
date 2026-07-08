package com.sakena.property.application

import com.sakena.property.application.command.CreateBuildingCommand
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildingServiceTest {

    private val buildingRepository = mockk<BuildingRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val service = BuildingService(buildingRepository, apartmentRepository)

    @Test
    fun `create persists a building`() {
        val saved = slot<Building>()
        every { buildingRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateBuildingCommand("Tower A", "Main Street"))

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
    fun `delete rejects building with apartments`() {
        val id = BuildingId.new()
        every { buildingRepository.existsById(id) } returns true
        every { apartmentRepository.existsByBuildingId(id) } returns true

        assertFailsWith<DomainConflictException> { service.delete(id) }
        verify(exactly = 0) { buildingRepository.deleteById(any()) }
    }
}
