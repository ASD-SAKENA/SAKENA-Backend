package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.property.infrastructure.web.dto.CreateBuildingRequest
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
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

@AutoConfigureMockMvc
class BuildingControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) : IntegrationTest() {

    @Test
    fun `full building lifecycle over HTTP`() {
        val createBody = objectMapper.writeValueAsString(CreateBuildingRequest("Tower A", "Main Street"))
        val created = mockMvc.perform(
            post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content(createBody),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Tower A"))
            .andExpect(jsonPath("$.address").value("Main Street"))
            .andReturn()

        val id = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(get("/api/v1/buildings/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))

        val updateBody = objectMapper.writeValueAsString(UpdateBuildingRequest("Tower B", "Second Street"))
        mockMvc.perform(put("/api/v1/buildings/$id").contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Tower B"))
            .andExpect(jsonPath("$.address").value("Second Street"))

        mockMvc.perform(get("/api/v1/buildings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$id')]").exists())

        mockMvc.perform(delete("/api/v1/buildings/$id")).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/buildings/$id")).andExpect(status().isNotFound)
    }

    @Test
    fun `creating a building with a blank name returns 400 with field errors`() {
        val body = objectMapper.writeValueAsString(CreateBuildingRequest("", "Address"))

        mockMvc.perform(post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
    }

    @Test
    fun `deleting a building with apartments returns 409`() {
        val buildingId = createBuilding("Tower With Apartment", "Main Street")
        createApartment(buildingId, "101")

        mockMvc.perform(delete("/api/v1/buildings/$buildingId"))
            .andExpect(status().isConflict)
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

    private fun createApartment(buildingId: String, unitNumber: String): String {
        val body = """
            {
              "buildingId": "$buildingId",
              "unitNumber": "$unitNumber",
              "floorNumber": 1,
              "areaSquareMeters": 80.50,
              "bedrooms": 2
            }
        """.trimIndent()
        val created = mockMvc.perform(
            post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(created.response.contentAsString).get("id").asText()
    }
}
