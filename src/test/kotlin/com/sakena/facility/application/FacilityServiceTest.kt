package com.sakena.facility.application

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FacilityServiceTest {

    private val repository = mockk<FacilityRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val service = FacilityService(repository, buildingAccess)
    private val buildingId = BuildingId.new()
    private val manager = user(Role.MANAGER)
    private val resident = user(Role.RESIDENT)

    @Test
    fun `create persists a new facility`() {
        val saved = slot<Facility>()
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateFacilityCommand("Pool", "pool"), manager)

        assertEquals("Pool", result.name)
        assertEquals(buildingId, result.buildingId)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `update renames an existing facility`() {
        val existing = Facility.create(buildingId, "Pool", "pool")
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { repository.findByIdAndBuildingId(existing.id, buildingId) } returns existing
        every { repository.save(any()) } answers { firstArg() }

        val result = service.update(
            existing.id,
            UpdateFacilityCommand("Gym", "fitness_center", 15),
            manager,
        )

        assertEquals("Gym", result.name)
        assertEquals(15, result.capacity)
    }

    @Test
    fun `update throws when the facility is missing`() {
        val id = FacilityId.new()
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { repository.findByIdAndBuildingId(id, buildingId) } returns null

        assertFailsWith<FacilityNotFoundException> {
            service.update(id, UpdateFacilityCommand("Gym", null, 10), manager)
        }
    }

    @Test
    fun `delete removes an existing facility`() {
        val id = FacilityId.new()
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { repository.existsByIdAndBuildingId(id, buildingId) } returns true
        justRun { repository.deleteById(id) }

        service.delete(id, manager)

        verify(exactly = 1) { repository.deleteById(id) }
    }

    @Test
    fun `delete throws when the facility is missing`() {
        val id = FacilityId.new()
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { repository.existsByIdAndBuildingId(id, buildingId) } returns false

        assertFailsWith<FacilityNotFoundException> { service.delete(id, manager) }
    }

    @Test
    fun `resident list is scoped to active residency building`() {
        val facility = Facility.create(buildingId, "Pool", "pool")
        every { buildingAccess.residentBuildingId(resident.id) } returns buildingId
        every { repository.findAllByBuildingId(buildingId) } returns listOf(facility)

        assertEquals(listOf(facility), service.getAll(resident))
    }

    @Test
    fun `manager cannot get a facility from another building`() {
        val id = FacilityId.new()
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { repository.findByIdAndBuildingId(id, buildingId) } returns null

        assertFailsWith<FacilityNotFoundException> { service.getById(id, manager) }
    }

    private fun user(role: Role) = User(
        id = UserId.generate(),
        username = "user-${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        passwordHash = "hash",
        role = role,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        managedBuildingId = buildingId.takeIf { role == Role.MANAGER },
    )
}
