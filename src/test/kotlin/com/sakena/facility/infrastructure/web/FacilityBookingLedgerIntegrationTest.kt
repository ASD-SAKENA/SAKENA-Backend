package com.sakena.facility.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.IntegrationTest
import com.sakena.facility.domain.FacilityRepository
import com.sakena.facility.domain.model.BookingRules
import com.sakena.facility.domain.model.Facility
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Booking a paid facility moves money, so it must leave a trace the resident
 * can read back — the balance changing with nothing in the ledger is exactly
 * the bug this guards.
 */
@AutoConfigureMockMvc
class FacilityBookingLedgerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val walletRepository: WalletRepository,
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
    @Autowired private val residencyRepository: ResidencyRepository,
) : IntegrationTest() {

    private val zone = ZoneId.of("Asia/Tehran")

    @Test
    fun `booking a paid facility shows up in the resident's own ledger`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val manager = register("manager-$suffix", "MANAGER")
        val resident = register("resident-$suffix", "RESIDENT")
        val buildingId = manager.managedBuildingId ?: error("Manager has no building")
        startResidency(resident.id, suffix, buildingId)

        val facility = facilityRepository.save(
            Facility.create(
                buildingId = buildingId,
                name = "استخر-$suffix",
                icon = "pool",
                capacity = 20,
                rules = paidRules,
            ),
        )

        topUp(resident.token, BigDecimal("500000"))

        val start = tomorrowAt(10)
        mockMvc.perform(
            post("/api/v1/facilities/${facility.id.value}/bookings")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startsAt":"$start","endsAt":"${start.plus(1, ChronoUnit.HOURS)}","partySize":2}
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        // Two people for one hour at 50,000 each.
        val wallet = walletRepository.findByOwner(resident.id) ?: error("No wallet")
        assertEquals(0, wallet.balance.compareTo(BigDecimal("400000")))

        mockMvc.perform(
            get("/api/v1/wallets/me/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].direction").value("DEBIT"))
            .andExpect(jsonPath("$[0].amount").value(100000))
            .andExpect(jsonPath("$[0].balanceAfter").value(400000))
    }

    @Test
    fun `cancelling puts the refund in the ledger too`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val manager = register("manager-$suffix", "MANAGER")
        val resident = register("resident-$suffix", "RESIDENT")
        val buildingId = manager.managedBuildingId ?: error("Manager has no building")
        startResidency(resident.id, suffix, buildingId)

        val facility = facilityRepository.save(
            Facility.create(buildingId, "سالن-$suffix", "gym", 20, paidRules),
        )
        topUp(resident.token, BigDecimal("500000"))

        val start = tomorrowAt(10)
        val created = mockMvc.perform(
            post("/api/v1/facilities/${facility.id.value}/bookings")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startsAt":"$start","endsAt":"${start.plus(1, ChronoUnit.HOURS)}","partySize":1}
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()
        val bookingId = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/facilities/${facility.id.value}/bookings/$bookingId")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token)),
        ).andExpect(status().isNoContent)

        val wallet = walletRepository.findByOwner(resident.id) ?: error("No wallet")
        assertEquals(0, wallet.balance.compareTo(BigDecimal("500000")))

        mockMvc.perform(
            get("/api/v1/wallets/me/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(resident.token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].direction").value("CREDIT"))
            .andExpect(jsonPath("$[0].amount").value(50000))
    }

    private val paidRules = BookingRules(
        opensAt = LocalTime.of(6, 0),
        closesAt = LocalTime.of(23, 0),
        closedDays = emptySet<DayOfWeek>(),
        minDurationMinutes = 30,
        maxDurationMinutes = 240,
        maxAdvanceDays = 30,
        maxPerResidentPerWeek = 0,
        hourlyPrice = BigDecimal("50000"),
    )

    /** Tomorrow at a fixed local hour, so the slot is always bookable. */
    private fun tomorrowAt(hour: Int): Instant =
        Instant.now().atZone(zone)
            .plusDays(1)
            .withHour(hour)
            .truncatedTo(ChronoUnit.HOURS)
            .toInstant()

    private fun topUp(token: String, amount: BigDecimal) {
        mockMvc.perform(
            post("/api/v1/wallets/me/top-ups")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":$amount}"""),
        ).andExpect(status().isCreated)
    }

    private fun startResidency(residentId: UserId, suffix: String, buildingId: BuildingId) {
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
            Residency.start(apartment.id, residentId, TenancyType.TENANT),
        )
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
