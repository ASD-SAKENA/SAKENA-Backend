package com.sakena.facility.application

import com.sakena.facility.application.command.CreateFacilityCommand
import com.sakena.facility.application.command.UpdateFacilityCommand
import com.sakena.facility.domain.FacilityNotFoundException
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.Facility
import com.sakena.facility.domain.model.FacilityId
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FacilityServiceTest {

    private val repository = mockk<FacilityRepository>()
    private val service = FacilityService(repository)

    @Test
    fun `create persists a new facility`() {
        val saved = slot<Facility>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(CreateFacilityCommand("Pool", "pool"))

        assertEquals("Pool", result.name)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `update renames an existing facility`() {
        val existing = Facility.create("Pool", "pool")
        every { repository.findById(existing.id) } returns existing
        every { repository.save(any()) } answers { firstArg() }

        val result = service.update(existing.id, UpdateFacilityCommand("Gym", "fitness_center"))

        assertEquals("Gym", result.name)
    }

    @Test
    fun `update throws when the facility is missing`() {
        val id = FacilityId.new()
        every { repository.findById(id) } returns null

        assertFailsWith<FacilityNotFoundException> {
            service.update(id, UpdateFacilityCommand("Gym", null))
        }
    }

    @Test
    fun `delete removes an existing facility`() {
        val id = FacilityId.new()
        every { repository.existsById(id) } returns true
        justRun { repository.deleteById(id) }

        service.delete(id)

        verify(exactly = 1) { repository.deleteById(id) }
    }

    @Test
    fun `delete throws when the facility is missing`() {
        val id = FacilityId.new()
        every { repository.existsById(id) } returns false

        assertFailsWith<FacilityNotFoundException> { service.delete(id) }
    }
}
