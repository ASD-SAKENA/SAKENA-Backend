package com.sakena.user.application

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.Role
import com.sakena.user.domain.StaffBuildingMembershipRepository
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StaffDirectoryServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val staffMembershipRepository = mockk<StaffBuildingMembershipRepository>()
    private val service = StaffDirectoryService(userRepository, staffMembershipRepository)

    private val buildingId = BuildingId.new()

    private fun user(role: Role, active: Boolean = true) = User.reconstitute(
        id = UserId.generate(),
        username = "user-${role.name.lowercase()}-$active",
        email = "${role.name.lowercase()}-$active@sakena.test",
        passwordHash = "hashed",
        role = role,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        active = active,
        managedBuildingId = if (role == Role.MANAGER) BuildingId.new() else null,
    )

    @Test
    fun `getActiveStaff returns only active staff, excluding every other role and inactive staff`() {
        val activeStaff = user(Role.STAFF, active = true)
        val members = listOf(
            activeStaff,
            user(Role.STAFF, active = false),
            user(Role.RESIDENT, active = true),
            user(Role.MANAGER, active = true),
            user(Role.ADMIN, active = true),
        )
        every { staffMembershipRepository.findStaffIdsByBuilding(buildingId) } returns
            members.map { it.id }
        every { userRepository.findAllByIds(members.map { it.id }.toSet()) } returns members

        val result = service.getActiveStaff(buildingId)

        assertEquals(listOf(activeStaff), result)
    }

    @Test
    fun `getActiveStaff only sees staff serving the requested building`() {
        val ourStaff = user(Role.STAFF)
        every { staffMembershipRepository.findStaffIdsByBuilding(buildingId) } returns
            listOf(ourStaff.id)
        every { userRepository.findAllByIds(setOf(ourStaff.id)) } returns listOf(ourStaff)

        val result = service.getActiveStaff(buildingId)

        assertEquals(listOf(ourStaff), result)
        // Another building's staff are never even loaded, let alone filtered out.
        verify(exactly = 1) { staffMembershipRepository.findStaffIdsByBuilding(buildingId) }
    }

    @Test
    fun `a building with no staff yet returns nothing without hitting the user store`() {
        every { staffMembershipRepository.findStaffIdsByBuilding(buildingId) } returns emptyList()

        assertTrue(service.getActiveStaff(buildingId).isEmpty())

        verify(exactly = 0) { userRepository.findAllByIds(any()) }
    }

    @Test
    fun `a requester who manages no building is refused`() {
        assertFailsWith<DomainForbiddenException> {
            service.getActiveStaff(requesterManagedBuildingId = null)
        }
    }
}
