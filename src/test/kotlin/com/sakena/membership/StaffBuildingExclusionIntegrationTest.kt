package com.sakena.membership

import com.sakena.IntegrationTest
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.Building
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Staff belong to no building or unit. The guards came after the fact, so
 * V37 clears anyone who slipped in earlier — this exercises that SQL against
 * the real schema, since a migration that does not match the columns it
 * targets fails silently by matching no rows.
 */
class StaffBuildingExclusionIntegrationTest(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val buildingRepository: BuildingRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
    @Autowired private val residencyRepository: ResidencyRepository,
) : IntegrationTest() {

    @Test
    fun `the cleanup vacates a unit a staff account still occupies`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val staff = userRepository.save(
            User.register(
                username = "staff-$suffix",
                email = "staff-$suffix@example.com",
                rawPassword = "password123",
                passwordEncoder = { it },
                role = Role.STAFF,
            ),
        )
        val building = buildingRepository.save(Building.create("Tower $suffix", "Somewhere"))
        val apartment = apartmentRepository.save(
            Apartment.create(building.id, "U-$suffix", 1, BigDecimal("80"), 2),
        )
        // Bypass the service guard on purpose: this is the legacy state the
        // migration exists to clean up.
        val residency = residencyRepository.save(
            Residency.start(apartment.id, staff.id, TenancyType.TENANT),
        )
        assertEquals(true, residency.active)

        applyCleanup()

        assertNull(residencyRepository.findActiveByResident(staff.id))
    }

    @Test
    fun `the cleanup leaves a genuine resident in place`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val resident = userRepository.save(
            User.register(
                username = "resident-$suffix",
                email = "resident-$suffix@example.com",
                rawPassword = "password123",
                passwordEncoder = { it },
                role = Role.RESIDENT,
            ),
        )
        val building = buildingRepository.save(Building.create("Tower $suffix", "Somewhere"))
        val apartment = apartmentRepository.save(
            Apartment.create(building.id, "U-$suffix", 1, BigDecimal("80"), 2),
        )
        residencyRepository.save(Residency.start(apartment.id, resident.id, TenancyType.TENANT))

        applyCleanup()

        assertEquals(apartment.id, residencyRepository.findActiveByResident(resident.id)?.apartmentId)
    }

    /** The same statements V37 runs, so the SQL is verified against the schema. */
    private fun applyCleanup() {
        jdbc.execute(
            """
            UPDATE residencies r
            SET moved_out_at = NOW()
            FROM users u
            WHERE r.resident_id = u.id
              AND u.role = 'STAFF'
              AND r.moved_out_at IS NULL
            """.trimIndent(),
        )
        jdbc.execute(
            """
            UPDATE building_invitations i
            SET status = 'REVOKED', accepted_by = NULL, accepted_at = NULL
            FROM users u
            WHERE i.accepted_by = u.id
              AND u.role = 'STAFF'
              AND i.role <> 'STAFF'
            """.trimIndent(),
        )
        jdbc.execute(
            """
            UPDATE building_invitations
            SET apartment_id = NULL, tenancy = NULL
            WHERE role = 'STAFF' AND apartment_id IS NOT NULL
            """.trimIndent(),
        )
    }
}
