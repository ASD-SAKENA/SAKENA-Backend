package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.property.application.BuildingService
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.property.infrastructure.web.dto.UpdateBuildingRequest
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.web.GlobalExceptionHandler
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.time.Instant

class BuildingControllerTest {

    private val buildingService = mockk<BuildingService>()
    private val profileService = mockk<ProfileService>()
    private val objectMapper = jacksonObjectMapper()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(BuildingController(buildingService, profileService))
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator())
        .build()

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `update maps request to command and uses the authenticated manager's own building`() {
        val building = Building.create("Tower B", "Second Street")
        val manager = authenticateManager(building.id)
        every {
            buildingService.update(
                building.id,
                manager.managedBuildingId,
                match { it.name == "Tower B" && it.address == "Second Street" },
            )
        } returns building

        val body = objectMapper.writeValueAsString(UpdateBuildingRequest("Tower B", "Second Street"))
        mockMvc.perform(put("/api/v1/buildings/${building.id}").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Tower B"))
            .andExpect(jsonPath("$.address").value("Second Street"))
    }

    @Test
    fun `update on a building the manager does not administer returns 403`() {
        val ownedBuildingId = BuildingId.new()
        val otherBuildingId = BuildingId.new()
        val manager = authenticateManager(ownedBuildingId)
        every {
            buildingService.update(otherBuildingId, manager.managedBuildingId, any())
        } throws DomainForbiddenException("You do not manage building '$otherBuildingId'")

        val body = objectMapper.writeValueAsString(UpdateBuildingRequest("Tower B", "Second Street"))
        mockMvc.perform(
            put("/api/v1/buildings/$otherBuildingId").contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isForbidden)
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

    private fun authenticateManager(managedBuildingId: BuildingId): User {
        val now = Instant.now()
        val manager = User.reconstitute(
            id = UserId.generate(),
            username = "manager-${managedBuildingId}",
            email = "manager@example.com",
            passwordHash = "hash",
            role = Role.MANAGER,
            createdAt = now,
            updatedAt = now,
            active = true,
            managedBuildingId = managedBuildingId,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(manager.username, null)
        every { profileService.getUserByUsername(manager.username) } returns manager
        return manager
    }

    private fun validator(): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().also { it.afterPropertiesSet() }
}
