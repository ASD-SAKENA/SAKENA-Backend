package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
import com.sakena.support.TestAuth
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class BuildingControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) : IntegrationTest() {

    @Test
    fun `a building is auto-created and assigned when a manager registers`() {
        val manager = TestAuth.registerManagerWithBuilding(mockMvc, objectMapper)

        mockMvc.perform(
            get("/api/v1/buildings/${manager.buildingId}")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(manager.token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(manager.buildingId))
    }

    @Test
    fun `a manager can rename and re-address the building they administer`() {
        val manager = TestAuth.registerManagerWithBuilding(mockMvc, objectMapper)

        val updateBody = objectMapper.writeValueAsString(UpdateBuildingRequest("Tower B", "Second Street"))
        mockMvc.perform(
            put("/api/v1/buildings/${manager.buildingId}")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Tower B"))
            .andExpect(jsonPath("$.address").value("Second Street"))
    }

    @Test
    fun `a manager cannot update a building they do not administer`() {
        val manager = TestAuth.registerManagerWithBuilding(mockMvc, objectMapper)
        val otherManager = TestAuth.registerManagerWithBuilding(mockMvc, objectMapper)

        val updateBody = objectMapper.writeValueAsString(UpdateBuildingRequest("Hijacked", "Nowhere"))
        mockMvc.perform(
            put("/api/v1/buildings/${otherManager.buildingId}")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `updating a building with a blank name returns 400 with field errors`() {
        val manager = TestAuth.registerManagerWithBuilding(mockMvc, objectMapper)
        val body = objectMapper.writeValueAsString(UpdateBuildingRequest("", "Address"))

        mockMvc.perform(
            put("/api/v1/buildings/${manager.buildingId}")
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
    }
}
