package com.sakena.property.application

import com.sakena.property.application.command.CreateApartmentCommand
import com.sakena.property.application.command.UpdateApartmentCommand
import com.sakena.property.domain.ApartmentNotFoundException
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApartmentServiceTest {

    private val apartmentRepository = mockk<ApartmentRepository>()
    private val buildingRepository = mockk<BuildingRepository>()
    private val service = ApartmentService(apartmentRepository, buildingRepository)

    @Test
    fun `create persists an apartment when the requester manages that building`() {
        val buildingId = BuildingId.new()
        val saved = slot<Apartment>()
        every { buildingRepository.existsById(buildingId) } returns true
        every { apartmentRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.create(
            CreateApartmentCommand(
                buildingId = buildingId,
                unitNumber = "12A",
                floorNumber = 3,
                areaSquareMeters = BigDecimal("85.50"),
                bedrooms = 2,
            ),
            requesterManagedBuildingId = buildingId,
        )

        assertEquals(buildingId, result.buildingId)
        assertEquals("12A", result.unitNumber)
        verify(exactly = 1) { apartmentRepository.save(any()) }
    }

    @Test
    fun `create is rejected for a manager who administers a different building`() {
        val buildingId = BuildingId.new()
        every { buildingRepository.existsById(buildingId) } returns true

        assertFailsWith<DomainForbiddenException> {
            service.create(
                CreateApartmentCommand(
                    buildingId = buildingId,
                    unitNumber = "1",
                    floorNumber = 0,
                    areaSquareMeters = BigDecimal("40"),
                    bedrooms = 1,
                ),
                requesterManagedBuildingId = BuildingId.new(),
            )
        }
        verify(exactly = 0) { apartmentRepository.save(any()) }
    }

    @Test
    fun `update is rejected when moving the apartment into a building the requester does not manage`() {
        val ownedBuildingId = BuildingId.new()
        val otherBuildingId = BuildingId.new()
        val apartment = Apartment.create(ownedBuildingId, "1", 1, BigDecimal("40"), 1)
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { buildingRepository.existsById(ownedBuildingId) } returns true
        every { buildingRepository.existsById(otherBuildingId) } returns true

        assertFailsWith<DomainForbiddenException> {
            service.update(
                apartment.id,
                UpdateApartmentCommand(
                    buildingId = otherBuildingId,
                    unitNumber = "1",
                    floorNumber = 1,
                    areaSquareMeters = BigDecimal("40"),
                    bedrooms = 1,
                ),
                requesterManagedBuildingId = ownedBuildingId,
            )
        }
        verify(exactly = 0) { apartmentRepository.save(any()) }
    }

    @Test
    fun `delete is rejected for a manager who does not administer the apartment's building`() {
        val buildingId = BuildingId.new()
        val apartment = Apartment.create(buildingId, "1", 1, BigDecimal("40"), 1)
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { buildingRepository.existsById(buildingId) } returns true

        assertFailsWith<DomainForbiddenException> {
            service.delete(apartment.id, requesterManagedBuildingId = BuildingId.new())
        }
        verify(exactly = 0) { apartmentRepository.deleteById(any()) }
    }

    @Test
    fun `getById throws when apartment is missing`() {
        val id = ApartmentId.new()
        every { apartmentRepository.findById(id) } returns null

        assertFailsWith<ApartmentNotFoundException> { service.getById(id) }
    }
}
