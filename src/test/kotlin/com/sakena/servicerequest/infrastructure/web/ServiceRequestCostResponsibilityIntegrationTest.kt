package com.sakena.servicerequest.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.Building
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.web.RegisterRequest
import com.sakena.wallet.domain.WalletRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

@AutoConfigureMockMvc
class ServiceRequestCostResponsibilityIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val serviceRequestRepository: ServiceRequestRepository,
    @Autowired private val walletRepository: WalletRepository,
    @Autowired private val buildingRepository: BuildingRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
    @Autowired private val residencyRepository: ResidencyRepository,
) : IntegrationTest() {

    @Test
    fun `manager selects responsibility and only building wallet settlement pays the worker`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val resident = register("resident-$suffix", "RESIDENT")
        val staff = register("staff-$suffix", "STAFF")
        val manager = register("manager-$suffix", "MANAGER")
        val requestingApartmentId = startResidency(resident, suffix)
        val requestId = completeServiceRequest(
            resident,
            staff,
            manager,
            requestingApartmentId,
        )
        val endpoint = "/api/v1/service-requests/$requestId/cost-responsibility"
        val validBody = objectMapper.writeValueAsBytes(
            AssignCostResponsibilityRequest(ServiceCostResponsibility.ALL_UNITS),
        )

        mockMvc.perform(
            patch(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            patch(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors[0].field").value("costResponsibility"))

        mockMvc.perform(
            patch(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"costResponsibility":"UNKNOWN"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Malformed request body"))

        val requestingUnitBody = objectMapper.writeValueAsBytes(
            AssignCostResponsibilityRequest(ServiceCostResponsibility.REQUESTING_UNIT),
        )
        mockMvc.perform(
            patch(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestingUnitBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.costResponsibility").value("REQUESTING_UNIT"))
            .andExpect(
                jsonPath("$.requestingApartmentId")
                    .value(requestingApartmentId.value.toString()),
            )

        mockMvc.perform(
            patch(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(requestId.toString()))
            .andExpect(jsonPath("$.costResponsibility").value("ALL_UNITS"))

        val persisted = serviceRequestRepository.findById(ServiceRequestId(requestId))
        assertEquals(ServiceCostResponsibility.ALL_UNITS, persisted?.costResponsibility)
        assertEquals(manager.id, persisted?.updatedBy)

        mockMvc.perform(
            post("/api/v1/wallets/settle/$requestId")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token)),
        ).andExpect(status().isConflict)

        assertEquals(
            ServiceRequestStatus.COMPLETED,
            serviceRequestRepository.findById(ServiceRequestId(requestId))?.status,
        )
        assertEquals(null, walletRepository.findByOwner(staff.id))

        val buildingBalanceBefore = walletRepository.findBuildingWallet()?.balance
            ?: error("Building wallet was not provisioned")
        val buildingWalletBody = objectMapper.writeValueAsBytes(
            AssignCostResponsibilityRequest(ServiceCostResponsibility.BUILDING_WALLET),
        )
        mockMvc.perform(
            patch(endpoint)
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildingWalletBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.costResponsibility").value("BUILDING_WALLET"))

        mockMvc.perform(
            post("/api/v1/wallets/settle/$requestId")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token)),
        ).andExpect(status().isNoContent)

        assertEquals(
            ServiceRequestStatus.SETTLED,
            serviceRequestRepository.findById(ServiceRequestId(requestId))?.status,
        )
        val workerWallet = walletRepository.findByOwner(staff.id)
            ?: error("Worker wallet was not created")
        assertEquals(0, workerWallet.balance.compareTo(BigDecimal("250.0")))
        val buildingBalanceAfter = walletRepository.findBuildingWallet()?.balance
            ?: error("Building wallet was not found after settlement")
        assertEquals(
            0,
            buildingBalanceAfter.compareTo(buildingBalanceBefore - BigDecimal("250.0")),
        )
    }

    private fun completeServiceRequest(
        resident: AuthenticatedUser,
        staff: AuthenticatedUser,
        manager: AuthenticatedUser,
        requestingApartmentId: ApartmentId,
    ): UUID {
        val createBody = CreateServiceRequestRequest(
            title = "Repair water pump",
            description = "The main water pump needs repair",
            location = "Basement",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
        )
        val created = mockMvc.perform(
            post("/api/v1/service-requests")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createBody)),
        )
            .andExpect(status().isCreated)
            .andExpect(
                jsonPath("$.requestingApartmentId")
                    .value(requestingApartmentId.value.toString()),
            )
            .andReturn()
        val requestId = UUID.fromString(
            objectMapper.readTree(created.response.contentAsString).get("id").asText(),
        )

        mockMvc.perform(
            patch("/api/v1/service-requests/$requestId/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/v1/service-requests/$requestId/assign")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token))
                .param("workerId", staff.id.value.toString()),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/v1/service-requests/$requestId/start-progress")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/v1/service-requests/$requestId/complete")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        CompleteRequest(
                            completionReport = "Water pump repaired",
                            completionCost = 250.0,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        return requestId
    }

    private fun startResidency(
        resident: AuthenticatedUser,
        suffix: String,
    ): ApartmentId {
        val building = buildingRepository.save(
            Building.create("Building $suffix", "Address $suffix"),
        )
        val apartment = apartmentRepository.save(
            Apartment.create(
                buildingId = building.id,
                unitNumber = "UNIT-$suffix",
                floorNumber = 1,
                areaSquareMeters = BigDecimal("90"),
                bedrooms = 2,
            ),
        )
        residencyRepository.save(
            Residency.start(
                apartmentId = apartment.id,
                residentId = resident.id,
                tenancy = TenancyType.TENANT,
            ),
        )
        return apartment.id
    }

    private fun register(username: String, role: String): AuthenticatedUser {
        val request = RegisterRequest(
            username = username,
            email = "$username@example.com",
            password = "password123",
            role = role,
        )
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isCreated)
            .andReturn()
        val token = objectMapper.readTree(result.response.contentAsString).get("token").asText()
        val userId = userRepository.findByUsername(username)?.id
            ?: error("Registered user '$username' was not persisted")
        return AuthenticatedUser(token, userId)
    }

    private fun bearer(token: String) = "Bearer $token"

    private data class AuthenticatedUser(
        val token: String,
        val id: UserId,
    )
}
