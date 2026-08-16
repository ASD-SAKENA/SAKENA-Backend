package com.sakena.user.application

import com.sakena.membership.domain.StaffBuildingMembershipRepository
import com.sakena.membership.domain.model.StaffBuildingMembership
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.ApartmentId
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
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserAdminServiceTest {

    private val buildingId = BuildingId.new()
    private val manager = createUser("manager", "manager@example.com", Role.MANAGER)
    private lateinit var userRepository: UserRepository
    private lateinit var buildingAccess: BuildingAccess
    private lateinit var residencyRepository: ResidencyRepository
    private lateinit var staffMembershipRepository: StaffBuildingMembershipRepository
    private lateinit var service: UserAdminService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        buildingAccess = mockk()
        residencyRepository = mockk()
        staffMembershipRepository = mockk()
        service = UserAdminService(
            userRepository,
            buildingAccess,
            residencyRepository,
            staffMembershipRepository,
        )
        every { buildingAccess.managedBuildingId(manager.id) } returns buildingId
        every { residencyRepository.findActiveByBuilding(buildingId) } returns emptyList()
        every { staffMembershipRepository.findAllByBuilding(buildingId) } returns emptyList()
    }

    @Test
    fun `list returns only residents and staff assigned to the managed building`() {
        val resident = createUser("resident", "resident@example.com", Role.RESIDENT)
        val staff = createUser("staff", "staff@example.com", Role.STAFF)
        val memberIds = setOf(manager.id, resident.id, staff.id)
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency(resident.id))
        every { staffMembershipRepository.findAllByBuilding(buildingId) } returns
            listOf(StaffBuildingMembership.create(staff.id, buildingId))
        every { userRepository.findAllByIds(memberIds) } returns listOf(manager, resident, staff)

        val result = service.getUsers(manager.id)

        assertEquals(listOf(manager, resident, staff), result)
        verify(exactly = 1) { userRepository.findAllByIds(memberIds) }
    }

    @Test
    fun `list applies role filter after building scope`() {
        val staff = createUser("staff", "staff@example.com", Role.STAFF)
        val memberIds = setOf(manager.id, staff.id)
        every { staffMembershipRepository.findAllByBuilding(buildingId) } returns
            listOf(StaffBuildingMembership.create(staff.id, buildingId))
        every { userRepository.findAllByIds(memberIds) } returns listOf(manager, staff)

        assertEquals(listOf(staff), service.getUsers(manager.id, Role.STAFF))
    }

    @Test
    fun `list includes inactive members of the managed building`() {
        val inactive = createUser("inactive", "inactive@example.com", Role.RESIDENT, active = false)
        val memberIds = setOf(manager.id, inactive.id)
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency(inactive.id))
        every { userRepository.findAllByIds(memberIds) } returns listOf(manager, inactive)

        val result = service.getUsers(manager.id, Role.RESIDENT)

        assertEquals(listOf(inactive), result)
        assertFalse(result.single().active)
    }

    @Test
    fun `manager can change status of a resident in their building`() {
        val resident = createUser("resident", "resident@example.com", Role.RESIDENT)
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency(resident.id))
        every { userRepository.findById(resident.id) } returns resident
        every { userRepository.save(any()) } answers { firstArg() }

        val result = service.changeActiveStatus(resident.id, active = false, manager.id)

        assertFalse(result.active)
        verify(exactly = 1) { userRepository.save(result) }
    }

    @Test
    fun `manager can change specialty of staff in their building`() {
        val staff = createUser("staff", "staff@example.com", Role.STAFF)
        every { staffMembershipRepository.findAllByBuilding(buildingId) } returns
            listOf(StaffBuildingMembership.create(staff.id, buildingId))
        every { userRepository.findById(staff.id) } returns staff
        every { userRepository.save(any()) } answers { firstArg() }

        val result = service.changeSpecialty(staff.id, "Electrician", manager.id)

        assertEquals("Electrician", result.specialty)
        verify(exactly = 1) { userRepository.save(result) }
    }

    @Test
    fun `manager cannot mutate a user from another building`() {
        val outsider = createUser("outsider", "outsider@example.com", Role.RESIDENT)

        assertFailsWith<DomainForbiddenException> {
            service.changeActiveStatus(outsider.id, active = false, manager.id)
        }
        assertFailsWith<DomainForbiddenException> {
            service.changeSpecialty(outsider.id, "Plumber", manager.id)
        }

        verify(exactly = 0) { userRepository.findById(any()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `manager can reactivate their own account`() {
        val inactiveManager = manager.copy(active = false)
        every { userRepository.findById(manager.id) } returns inactiveManager
        every { userRepository.save(any()) } answers { firstArg() }

        val result = service.changeActiveStatus(manager.id, active = true, manager.id)

        assertTrue(result.active)
    }

    private fun residency(residentId: UserId): Residency = Residency.start(
        apartmentId = ApartmentId.new(),
        residentId = residentId,
        tenancy = TenancyType.TENANT,
    )

    private fun createUser(
        username: String,
        email: String,
        role: Role,
        active: Boolean = true,
    ): User = User.reconstitute(
        id = UserId.generate(),
        username = username,
        email = email,
        passwordHash = "hashed",
        role = role,
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        active = active,
    )
}
