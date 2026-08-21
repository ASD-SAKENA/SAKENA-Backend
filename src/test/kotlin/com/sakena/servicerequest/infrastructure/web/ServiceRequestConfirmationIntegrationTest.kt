package com.sakena.servicerequest.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.rating.application.RatingService
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.web.RegisterRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Drives the resident confirm-and-rate flow through real HTTP calls against a real
 * Postgres (via [IntegrationTest]'s Testcontainers setup), exercising the `rating/`
 * bounded context end to end: the `staff_ratings` migration/entity mapping, the
 * `findAverageByStaffIds` JPQL projection, and the cross-context transaction that
 * confirms the request and records the rating together.
 */
@AutoConfigureMockMvc
class ServiceRequestConfirmationIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val serviceRequestRepository: ServiceRequestRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
    @Autowired private val residencyRepository: ResidencyRepository,
    @Autowired private val ratingService: RatingService,
) : IntegrationTest() {

    @Test
    fun `resident confirms a completed request and rates the staff, and the average shows up for managers`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val resident = register("resident-$suffix", "RESIDENT")
        val staff = register("staff-$suffix", "STAFF")
        val manager = register("manager-$suffix", "MANAGER")
        val requestingApartmentId = startResidency(resident, suffix, manager)

        val createBody = CreateServiceRequestRequest(
            title = "Fix elevator button",
            description = "The third floor button is stuck",
            location = "Elevator lobby",
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELECTRICAL,
        )
        val created = mockMvc.perform(
            post("/api/v1/service-requests")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createBody)),
        )
            .andExpect(status().isCreated)
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
                            completionReport = "Button replaced",
                            completionCost = 40.0,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(
            patch("/api/v1/service-requests/$requestId/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(ConfirmCompletionRequest(score = 4))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))

        val persisted = serviceRequestRepository.findById(ServiceRequestId(requestId))
        assertEquals(ServiceRequestStatus.CONFIRMED, persisted?.status)

        val staffListResult = mockMvc.perform(
            get("/api/v1/staff")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token)),
        )
            .andExpect(status().isOk)
            .andReturn()
        val staffEntry = objectMapper.readTree(staffListResult.response.contentAsString)
            .first { it.get("id").asText() == staff.id.value.toString() }
        assertEquals(4.0, staffEntry.get("averageRating").asDouble())

        assertEquals(requestingApartmentId, persisted?.requestingApartmentId)
    }

    @Test
    fun `a second rating for the same service request is rejected by the staff_ratings unique constraint`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val resident = register("resident-$suffix", "RESIDENT")
        val staff = register("staff-$suffix", "STAFF")
        val manager = register("manager-$suffix", "MANAGER")
        startResidency(resident, suffix, manager)

        val createBody = CreateServiceRequestRequest(
            title = "Repair hallway flooring",
            description = "Hallway flooring is cracked",
            location = "2nd floor hallway",
            categoryGroup = ServiceCategoryGroup.BUILDING,
            subCategory = ServiceSubCategory.FLOORING,
        )
        val created = mockMvc.perform(
            post("/api/v1/service-requests")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createBody)),
        )
            .andExpect(status().isCreated)
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
                        CompleteRequest(completionReport = "Repainted", completionCost = 60.0),
                    ),
                ),
        ).andExpect(status().isOk)
        mockMvc.perform(
            patch("/api/v1/service-requests/$requestId/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(ConfirmCompletionRequest(score = 5))),
        ).andExpect(status().isOk)

        // A second confirm attempt through HTTP is rejected at the domain level (status
        // is no longer COMPLETED) before it ever reaches the rating write, so the unique
        // constraint on staff_ratings.service_request_id is never exercised by the normal
        // request flow. Drive RatingService directly, the same way confirmCompletionAndRate
        // does internally, to prove the DB constraint itself rejects a duplicate rating for
        // the same service request.
        val persisted = serviceRequestRepository.findById(ServiceRequestId(requestId))
            ?: error("Service request not found after confirmation")
        val staffId = persisted.assignedTo ?: error("Confirmed request has no assigned staff")

        assertThrows<DataIntegrityViolationException> {
            ratingService.rate(persisted.id, staffId, resident.id, 2)
        }

        val requestAfterSecondAttempt = serviceRequestRepository.findById(ServiceRequestId(requestId))
        assertEquals(ServiceRequestStatus.CONFIRMED, requestAfterSecondAttempt?.status)
    }

    private fun startResidency(
        resident: AuthenticatedUser,
        suffix: String,
        manager: AuthenticatedUser,
    ): com.sakena.property.domain.model.ApartmentId {
        val buildingId = manager.managedBuildingId ?: error("Manager has no managed building")
        val apartment = apartmentRepository.save(
            Apartment.create(
                buildingId = buildingId,
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
        val user = userRepository.findByUsername(username)
            ?: error("Registered user '$username' was not persisted")
        return AuthenticatedUser(token, user.id, user.managedBuildingId)
    }

    private fun bearer(token: String) = "Bearer $token"


    private data class AuthenticatedUser(
        val token: String,
        val id: UserId,
        val managedBuildingId: com.sakena.property.domain.model.BuildingId? = null,
    )
}
