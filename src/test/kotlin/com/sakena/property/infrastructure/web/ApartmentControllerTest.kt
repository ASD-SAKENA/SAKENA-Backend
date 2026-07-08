package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.property.application.ApartmentService
import com.sakena.property.domain.BuildingNotFoundException
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.math.BigDecimal
import java.util.UUID

class ApartmentControllerTest {

    private val apartmentService = mockk<ApartmentService>()
    private val objectMapper = jacksonObjectMapper()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ApartmentController(apartmentService))
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator())
        .build()

    @Test
    fun `create maps request to command and returns created apartment`() {
        val buildingId = BuildingId.new()
        val apartment = Apartment.create(buildingId, "101", 1, BigDecimal("80.50"), 2)
        every {
            apartmentService.create(
                match {
                    it.buildingId == buildingId &&
                        it.unitNumber == "101" &&
                        it.floorNumber == 1 &&
                        it.areaSquareMeters == BigDecimal("80.50") &&
                        it.bedrooms == 2
                },
            )
        } returns apartment

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(apartmentJson(buildingId.value)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(apartment.id.value.toString()))
            .andExpect(jsonPath("$.buildingId").value(buildingId.value.toString()))
            .andExpect(jsonPath("$.unitNumber").value("101"))
    }

    @Test
    fun `update maps request to command`() {
        val buildingId = BuildingId.new()
        val apartment = Apartment.create(buildingId, "202", 2, BigDecimal("95.25"), 3)
        every {
            apartmentService.update(
                apartment.id,
                match {
                    it.buildingId == buildingId &&
                        it.unitNumber == "202" &&
                        it.floorNumber == 2 &&
                        it.bedrooms == 3
                },
            )
        } returns apartment

        mockMvc.perform(put("/api/v1/apartments/${apartment.id}").contentType(MediaType.APPLICATION_JSON).content(apartmentJson(buildingId.value, "202", 2, "95.25", 3)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unitNumber").value("202"))
            .andExpect(jsonPath("$.bedrooms").value(3))
    }

    @Test
    fun `list maps optional building filter`() {
        val buildingId = BuildingId.new()
        every { apartmentService.getAll(buildingId) } returns emptyList()

        mockMvc.perform(get("/api/v1/apartments?buildingId=$buildingId"))
            .andExpect(status().isOk)

        verify(exactly = 1) { apartmentService.getAll(buildingId) }
    }

    @Test
    fun `missing building is translated to 404`() {
        val buildingId = BuildingId.new()
        every { apartmentService.create(any()) } throws BuildingNotFoundException(buildingId)

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(apartmentJson(buildingId.value)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `invalid area returns validation error`() {
        val body = apartmentJson(BuildingId.new().value, areaSquareMeters = "0.00")

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("areaSquareMeters"))
    }

    @Test
    fun `delete maps apartment id`() {
        val apartment = Apartment.create(BuildingId.new(), "303", 3, BigDecimal("70.00"), 1)
        every { apartmentService.delete(apartment.id) } returns Unit

        mockMvc.perform(delete("/api/v1/apartments/${apartment.id}"))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { apartmentService.delete(apartment.id) }
    }

    private fun apartmentJson(
        buildingId: UUID,
        unitNumber: String = "101",
        floorNumber: Int = 1,
        areaSquareMeters: String = "80.50",
        bedrooms: Int = 2,
    ): String = objectMapper.writeValueAsString(
        mapOf(
            "buildingId" to buildingId,
            "unitNumber" to unitNumber,
            "floorNumber" to floorNumber,
            "areaSquareMeters" to BigDecimal(areaSquareMeters),
            "bedrooms" to bedrooms,
        ),
    )

    private fun validator(): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().also { it.afterPropertiesSet() }
}
