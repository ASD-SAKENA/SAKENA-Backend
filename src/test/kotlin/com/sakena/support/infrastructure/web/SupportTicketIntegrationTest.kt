package com.sakena.support.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.user.infrastructure.web.RegisterRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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

@AutoConfigureMockMvc
class SupportTicketIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
    @Autowired private val residencyRepository: ResidencyRepository,
) : IntegrationTest() {

    @Test
    fun `a ticket runs from opening through reply, answer and reopening`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val manager = register("manager-$suffix", "MANAGER")
        val resident = register("resident-$suffix", "RESIDENT")
        startResidency(resident.id, suffix, manager.managedBuildingId!!)

        val ticketId = openTicket(resident.token, anonymous = false, suffix = suffix)

        // The manager sees it waiting, with the resident named.
        mockMvc.perform(get("/api/v1/support-tickets").header(HttpHeaders.AUTHORIZATION, bearer(manager.token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("AWAITING_REPLY"))
            .andExpect(jsonPath("$[0].raisedByName").value("resident-$suffix"))
            .andExpect(jsonPath("$[0].raisedByUnit").value("UNIT-$suffix"))

        reply(manager.token, ticketId, "پیگیری می‌کنم")
        mockMvc.perform(get("/api/v1/support-tickets/$ticketId").header(HttpHeaders.AUTHORIZATION, bearer(manager.token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[1].authorRole").value("MANAGER"))

        mockMvc.perform(
            patch("/api/v1/support-tickets/$ticketId/answer")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager.token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ANSWERED"))

        // The resident is not satisfied and continues the same thread.
        reply(resident.token, ticketId, "هنوز حل نشده")
        mockMvc.perform(get("/api/v1/support-tickets/$ticketId").header(HttpHeaders.AUTHORIZATION, bearer(resident.token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ticket.status").value("AWAITING_REPLY"))
            .andExpect(jsonPath("$.messages.length()").value(3))
    }

    @Test
    fun `an anonymous ticket never exposes the resident to the manager`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val manager = register("manager-$suffix", "MANAGER")
        val resident = register("resident-$suffix", "RESIDENT")
        startResidency(resident.id, suffix, manager.managedBuildingId!!)

        val ticketId = openTicket(resident.token, anonymous = true, suffix = suffix)

        // Neither the list nor the thread may name the resident or their unit.
        mockMvc.perform(get("/api/v1/support-tickets").header(HttpHeaders.AUTHORIZATION, bearer(manager.token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].anonymous").value(true))
            .andExpect(jsonPath("$[0].raisedByName").doesNotExist())
            .andExpect(jsonPath("$[0].raisedByUnit").doesNotExist())

        mockMvc.perform(get("/api/v1/support-tickets/$ticketId").header(HttpHeaders.AUTHORIZATION, bearer(manager.token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ticket.raisedByName").doesNotExist())
            .andExpect(jsonPath("$.ticket.raisedByUnit").doesNotExist())

        // The resident still sees their own ticket in full.
        mockMvc.perform(get("/api/v1/support-tickets/mine").header(HttpHeaders.AUTHORIZATION, bearer(resident.token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].raisedByName").value("resident-$suffix"))

        // Replies still reach the anonymous resident.
        reply(manager.token, ticketId, "بررسی می‌کنم")
        mockMvc.perform(get("/api/v1/support-tickets/mine").header(HttpHeaders.AUTHORIZATION, bearer(resident.token)))
            .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
    }

    @Test
    fun `staff are shut out of the feature entirely`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val manager = register("manager-$suffix", "MANAGER")
        val resident = register("resident-$suffix", "RESIDENT")
        val staff = register("staff-$suffix", "STAFF")
        startResidency(resident.id, suffix, manager.managedBuildingId!!)
        val ticketId = openTicket(resident.token, anonymous = false, suffix = suffix)

        mockMvc.perform(
            post("/api/v1/support-tickets")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"category":"COMPLAINT","subject":"x","body":"y"}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/support-tickets/$ticketId").header(HttpHeaders.AUTHORIZATION, bearer(staff.token)))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/support-tickets/mine").header(HttpHeaders.AUTHORIZATION, bearer(staff.token)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a neighbour cannot read someone else's ticket`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val manager = register("manager-$suffix", "MANAGER")
        val resident = register("resident-$suffix", "RESIDENT")
        val neighbour = register("neighbour-$suffix", "RESIDENT")
        startResidency(resident.id, suffix, manager.managedBuildingId!!)
        startResidency(neighbour.id, "n$suffix", manager.managedBuildingId!!)
        val ticketId = openTicket(resident.token, anonymous = false, suffix = suffix)

        mockMvc.perform(get("/api/v1/support-tickets/$ticketId").header(HttpHeaders.AUTHORIZATION, bearer(neighbour.token)))
            .andExpect(status().isForbidden)
    }

    private fun openTicket(token: String, anonymous: Boolean, suffix: String): String {
        val result = mockMvc.perform(
            post("/api/v1/support-tickets")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"category":"COMPLAINT","subject":"سر و صدا-$suffix",
                     "body":"هر شب صدا می‌آید","anonymous":$anonymous}
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun reply(token: String, ticketId: String, body: String) {
        mockMvc.perform(
            post("/api/v1/support-tickets/$ticketId/messages")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"$body","kind":"TEXT"}"""),
        ).andExpect(status().isCreated)
    }

    private fun startResidency(residentId: UserId, suffix: String, buildingId: BuildingId) {
        val apartment = apartmentRepository.save(
            Apartment.create(buildingId, "UNIT-$suffix", 1, BigDecimal("90"), 2),
        )
        residencyRepository.save(Residency.start(apartment.id, residentId, TenancyType.TENANT))
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
        ).andExpect(status().isCreated).andReturn()
        val token = objectMapper.readTree(result.response.contentAsString).get("token").asText()
        val user = userRepository.findByUsername(username) ?: error("User not persisted")
        return AuthenticatedUser(token, user.id, user.managedBuildingId)
    }

    private fun bearer(token: String) = "Bearer $token"

    private data class AuthenticatedUser(
        val token: String,
        val id: UserId,
        val managedBuildingId: BuildingId? = null,
    )
}
