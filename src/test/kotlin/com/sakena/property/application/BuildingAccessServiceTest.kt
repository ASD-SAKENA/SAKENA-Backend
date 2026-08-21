package com.sakena.property.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BuildingAccessServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val service = BuildingAccessService(
        userRepository,
        residencyRepository,
        apartmentRepository,
    )

    @Test
    fun `resolves the building assigned to a manager`() {
        val building = Building.create("Tower", "Address")
        val manager = user(Role.MANAGER, building.id)
        every { userRepository.findById(manager.id) } returns manager

        assertEquals(building.id, service.managedBuildingId(manager.id))
        assertEquals(building.id, service.buildingIdFor(manager))
    }

    @Test
    fun `resolves a resident building through the active apartment`() {
        val resident = user(Role.RESIDENT)
        val building = Building.create("Tower", "Address")
        val apartment = Apartment.create(
            building.id,
            "101",
            1,
            BigDecimal("80.00"),
            2,
        )
        val residency = Residency.start(apartment.id, resident.id, TenancyType.TENANT)
        every { residencyRepository.findActiveByResident(resident.id) } returns residency
        every { apartmentRepository.findById(apartment.id) } returns apartment

        assertEquals(building.id, service.residentBuildingId(resident.id))
        assertEquals(building.id, service.buildingIdFor(resident))
    }

    @Test
    fun `staff and administrators have no building scope`() {
        assertNull(service.buildingIdFor(user(Role.STAFF)))
        assertNull(service.buildingIdFor(user(Role.ADMIN)))
    }

    @Test
    fun `resident without an active residency has no building scope`() {
        val resident = user(Role.RESIDENT)
        every { residencyRepository.findActiveByResident(resident.id) } returns null

        assertNull(service.buildingIdFor(resident))
    }

    @Test
    fun `missing manager record has no building scope`() {
        val manager = user(Role.MANAGER, Building.create("Tower", "Address").id)
        every { userRepository.findById(manager.id) } returns null

        assertNull(service.buildingIdFor(manager))
    }

    @Test
    fun `rejects access when the manager owns another building`() {
        val building = Building.create("Tower", "Address")
        val manager = user(Role.MANAGER, building.id)
        every { userRepository.findById(manager.id) } returns manager

        assertFailsWith<DomainForbiddenException> {
            service.requireManagerAccess(Building.create("Other", "Other").id, manager.id)
        }
    }

    @Test
    fun `rejects a non-manager identity as a building manager`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident

        assertFailsWith<DomainForbiddenException> {
            service.managedBuildingId(resident.id)
        }
    }

    private fun user(role: Role, buildingId: BuildingId? = null): User {
        val id = UserId.generate()
        val now = Instant.now()
        return User.reconstitute(
            id = id,
            username = "user-${id.value}",
            email = "${id.value}@example.com",
            passwordHash = "hash",
            role = role,
            createdAt = now,
            updatedAt = now,
            active = true,
            managedBuildingId = buildingId,
        )
    }
}
