package com.sakena.property.application

import com.sakena.property.application.command.CreateBuildingCommand
import com.sakena.property.application.command.UpdateBuildingCommand
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildingServiceTest {

    private val managerId = UserId.generate()
    private val buildingRepository = mockk<BuildingRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val service = BuildingService(buildingRepository, apartmentRepository)

    @Test
    fun `create persists a building`() {
        val saved = slot<Building>()
        every { buildingRepository.findByManagerId(managerId) } returns null
        every { buildingRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateBuildingCommand("Tower A", "Main Street"), managerId)

        assertEquals("Tower A", result.name)
        assertEquals("Main Street", result.address)
        assertEquals(managerId, result.managerId)
        verify(exactly = 1) { buildingRepository.save(any()) }
    }

    @Test
    fun `create rejects a second building for the same manager`() {
        every { buildingRepository.findByManagerId(managerId) } returns
            Building.create("Existing", "Existing address", managerId)

        assertFailsWith<DomainConflictException> {
            service.create(CreateBuildingCommand("Another", "Another address"), managerId)
        }
        verify(exactly = 0) { buildingRepository.save(any()) }
    }

    @Test
    fun `getById throws when building is missing`() {
        val id = BuildingId.new()
        every { buildingRepository.findById(id) } returns null

        assertFailsWith<BuildingNotFoundException> { service.getById(id, managerId) }
    }

    @Test
    fun `delete rejects building with apartments`() {
        val id = BuildingId.new()
        every { buildingRepository.findById(id) } returns
            Building.reconstitute(id, managerId, "Tower", "Address", java.time.Instant.now(), java.time.Instant.now())
        every { apartmentRepository.existsByBuildingId(id) } returns true

        assertFailsWith<DomainConflictException> { service.delete(id, managerId) }
        verify(exactly = 0) { buildingRepository.deleteById(any()) }
    }

    @Test
    fun `manager cannot update another manager's building`() {
        val otherManagerId = UserId.generate()
        val building = Building.create("Other", "Other address", otherManagerId)
        every { buildingRepository.findById(building.id) } returns building

        assertFailsWith<DomainForbiddenException> {
            service.update(building.id, UpdateBuildingCommand("Changed", "Changed address"), managerId)
        }
        verify(exactly = 0) { buildingRepository.save(any()) }
    }
}
