package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.property.infrastructure.web.dto.CreateBuildingRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class ApartmentControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) : IntegrationTest() {

    @Test
    fun `full apartment lifecycle over HTTP`() {
        val buildingId = createBuilding("Tower C", "Third Street")
        val created = createApartment(buildingId, "301")
        val apartmentId = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(get("/api/v1/apartments/$apartmentId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(apartmentId))
            .andExpect(jsonPath("$.buildingId").value(buildingId))

        val updateBody = apartmentJson(buildingId, "302", 3, "95.25", 3)
        mockMvc.perform(put("/api/v1/apartments/$apartmentId").contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unitNumber").value("302"))
            .andExpect(jsonPath("$.floorNumber").value(3))
            .andExpect(jsonPath("$.bedrooms").value(3))

        mockMvc.perform(get("/api/v1/apartments?buildingId=$buildingId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$apartmentId')]").exists())

        mockMvc.perform(delete("/api/v1/apartments/$apartmentId")).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/apartments/$apartmentId")).andExpect(status().isNotFound)
    }

    @Test
    fun `creating an apartment for a missing building returns 404`() {
        val missingBuildingId = UUID.randomUUID().toString()
        val body = apartmentJson(missingBuildingId, "404", 4, "70.00", 1)

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `creating an apartment with invalid area returns 400 with field errors`() {
        val buildingId = createBuilding("Tower D", "Fourth Street")
        val body = apartmentJson(buildingId, "401", 4, "0.00", 1)

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("areaSquareMeters"))
    }

    private fun createBuilding(name: String, address: String): String {
        val body = objectMapper.writeValueAsString(CreateBuildingRequest(name, address))
        val created = mockMvc.perform(
            post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(created.response.contentAsString).get("id").asText()
    }

    private fun createApartment(buildingId: String, unitNumber: String) =
        mockMvc.perform(
            post("/api/v1/apartments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apartmentJson(buildingId, unitNumber, 3, "85.50", 2)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.unitNumber").value(unitNumber))
            .andExpect(jsonPath("$.buildingId").value(buildingId))
            .andReturn()

    private fun apartmentJson(
        buildingId: String,
        unitNumber: String,
        floorNumber: Int,
        areaSquareMeters: String,
        bedrooms: Int,
    ): String = """
        {
          "buildingId": "$buildingId",
          "unitNumber": "$unitNumber",
          "floorNumber": $floorNumber,
          "areaSquareMeters": $areaSquareMeters,
          "bedrooms": $bedrooms
        }
    """.trimIndent()
}
