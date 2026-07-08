package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.property.application.BuildingService
import com.sakena.property.domain.model.Building
import com.sakena.property.infrastructure.web.dto.CreateBuildingRequest
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
import com.sakena.shared.domain.DomainConflictException
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

class BuildingControllerTest {

    private val buildingService = mockk<BuildingService>()
    private val objectMapper = jacksonObjectMapper()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(BuildingController(buildingService))
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator())
        .build()

    @Test
    fun `create maps request to command and returns created building`() {
        val building = Building.create("Tower A", "Main Street")
        every { buildingService.create(match { it.name == "Tower A" && it.address == "Main Street" }) } returns building

        val body = objectMapper.writeValueAsString(CreateBuildingRequest("Tower A", "Main Street"))
        mockMvc.perform(post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(building.id.value.toString()))
            .andExpect(jsonPath("$.name").value("Tower A"))
            .andExpect(jsonPath("$.address").value("Main Street"))
    }

    @Test
    fun `update maps request to command`() {
        val building = Building.create("Tower B", "Second Street")
        every { buildingService.update(building.id, match { it.name == "Tower B" && it.address == "Second Street" }) } returns building

        val body = objectMapper.writeValueAsString(UpdateBuildingRequest("Tower B", "Second Street"))
        mockMvc.perform(put("/api/v1/buildings/${building.id}").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Tower B"))
            .andExpect(jsonPath("$.address").value("Second Street"))
    }

    @Test
    fun `blank name returns validation error`() {
        val body = objectMapper.writeValueAsString(CreateBuildingRequest("", "Main Street"))

        mockMvc.perform(post("/api/v1/buildings").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
    }

    @Test
    fun `delete conflict is translated to 409`() {
        val building = Building.create("Tower C", "Third Street")
        every { buildingService.delete(building.id) } throws DomainConflictException("Cannot delete building")

        mockMvc.perform(delete("/api/v1/buildings/${building.id}"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `list returns building responses`() {
        val building = Building.create("Tower D", "Fourth Street")
        every { buildingService.getAll() } returns listOf(building)

        mockMvc.perform(get("/api/v1/buildings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(building.id.value.toString()))
            .andExpect(jsonPath("$[0].name").value("Tower D"))

        verify(exactly = 1) { buildingService.getAll() }
    }

    private fun validator(): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().also { it.afterPropertiesSet() }
}
