package com.sakena.property.infrastructure.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sakena.property.application.ApartmentService
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ApartmentControllerTest {

    private val apartmentService = mockk<ApartmentService>()
    private val profileService = mockk<ProfileService>()
    private val objectMapper = jacksonObjectMapper()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ApartmentController(apartmentService, profileService))
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator())
        .build()

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `create maps request to command and uses the authenticated manager's own building`() {
        val buildingId = BuildingId.new()
        val manager = authenticateManager(buildingId)
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
                manager.managedBuildingId,
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
        val manager = authenticateManager(buildingId)
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
                manager.managedBuildingId,
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
    fun `create on a building the manager does not administer returns 403`() {
        val ownedBuildingId = BuildingId.new()
        val otherBuildingId = BuildingId.new()
        val manager = authenticateManager(ownedBuildingId)
        every {
            apartmentService.create(any(), manager.managedBuildingId)
        } throws DomainForbiddenException("You do not manage building '$otherBuildingId'")

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(apartmentJson(otherBuildingId.value)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `invalid area returns validation error`() {
        authenticateManager(BuildingId.new())
        val body = apartmentJson(BuildingId.new().value, areaSquareMeters = "0.00")

        mockMvc.perform(post("/api/v1/apartments").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("areaSquareMeters"))
    }

    @Test
    fun `delete maps apartment id`() {
        val buildingId = BuildingId.new()
        val manager = authenticateManager(buildingId)
        val apartment = Apartment.create(buildingId, "303", 3, BigDecimal("70.00"), 1)
        every { apartmentService.delete(apartment.id, manager.managedBuildingId) } returns Unit

        mockMvc.perform(delete("/api/v1/apartments/${apartment.id}"))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { apartmentService.delete(apartment.id, manager.managedBuildingId) }
    }

    private fun authenticateManager(managedBuildingId: BuildingId): User {
        val now = Instant.now()
        val manager = User.reconstitute(
            id = UserId.generate(),
            username = "manager-$managedBuildingId",
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
