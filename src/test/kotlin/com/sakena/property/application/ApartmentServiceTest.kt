package com.sakena.property.application

import com.sakena.property.application.command.CreateApartmentCommand
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
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
    fun `create persists an apartment for an existing building`() {
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
        )

        assertEquals(buildingId, result.buildingId)
        assertEquals("12A", result.unitNumber)
        verify(exactly = 1) { apartmentRepository.save(any()) }
    }

    @Test
    fun `create throws when building is missing`() {
        val buildingId = BuildingId.new()
        every { buildingRepository.existsById(buildingId) } returns false

        assertFailsWith<BuildingNotFoundException> {
            service.create(
                CreateApartmentCommand(
                    buildingId = buildingId,
                    unitNumber = "1",
                    floorNumber = 0,
                    areaSquareMeters = BigDecimal("40"),
                    bedrooms = 1,
                ),
            )
        }
        verify(exactly = 0) { apartmentRepository.save(any()) }
    }
}
