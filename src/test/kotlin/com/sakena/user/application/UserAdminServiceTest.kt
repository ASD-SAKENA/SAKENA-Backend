package com.sakena.user.application

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class UserAdminServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userAdminService: UserAdminService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        userAdminService = UserAdminService(userRepository)
    }

    private fun createUser(
        username: String = "john",
        email: String = "john@example.com",
        role: Role = Role.RESIDENT,
        active: Boolean = true
    ): User {
        return User.reconstitute(
            id = UserId.generate(),
            username = username,
            email = email,
            passwordHash = "hashed",
            role = role,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = active,
            managedBuildingId = if (role == Role.MANAGER) BuildingId.new() else null,
        )
    }

    // ===== GET USERS TESTS =====

    @Test
    fun `getUsers should return all users when no role filter is given`() {
        val users = listOf(
            createUser(username = "resident", role = Role.RESIDENT),
            createUser(username = "manager", email = "manager@example.com", role = Role.MANAGER),
            createUser(username = "staff", email = "staff@example.com", role = Role.STAFF)
        )
        every { userRepository.findAll() } returns users

        val result = userAdminService.getUsers()

        assertEquals(3, result.size)
        assertEquals(users, result)
        verify(exactly = 1) { userRepository.findAll() }
    }

    @Test
    fun `getUsers should only return users with the requested role`() {
        val staff = createUser(username = "staff", email = "staff@example.com", role = Role.STAFF)
        every { userRepository.findAll() } returns listOf(
            createUser(username = "resident", role = Role.RESIDENT),
            staff,
            createUser(username = "manager", email = "manager@example.com", role = Role.MANAGER)
        )

        val result = userAdminService.getUsers(Role.STAFF)

        assertEquals(listOf(staff), result)
    }

    @Test
    fun `getUsers should return empty list when no user matches the role`() {
        every { userRepository.findAll() } returns listOf(
            createUser(username = "resident", role = Role.RESIDENT)
        )

        val result = userAdminService.getUsers(Role.STAFF)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUsers should include inactive users`() {
        val inactive = createUser(username = "inactive", email = "inactive@example.com", active = false)
        every { userRepository.findAll() } returns listOf(inactive)

        val result = userAdminService.getUsers()

        assertEquals(1, result.size)
        assertFalse(result.first().active)
    }

    // ===== CHANGE ACTIVE STATUS TESTS =====

    @Test
    fun `changeActiveStatus should deactivate an active user`() {
        val user = createUser(active = true)
        every { userRepository.findById(user.id) } returns user
        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userAdminService.changeActiveStatus(user.id, active = false)

        assertFalse(result.active)
        assertFalse(savedUserSlot.captured.active)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `changeActiveStatus should reactivate an inactive user`() {
        val user = createUser(active = false)
        every { userRepository.findById(user.id) } returns user
        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userAdminService.changeActiveStatus(user.id, active = true)

        assertTrue(result.active)
        assertTrue(savedUserSlot.captured.active)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `changeActiveStatus should throw EntityNotFoundException when user does not exist`() {
        val userId = UserId.generate()
        every { userRepository.findById(userId) } returns null

        assertThrows<EntityNotFoundException> {
            userAdminService.changeActiveStatus(userId, active = false)
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ===== CHANGE ROLE TESTS =====

    @Test
    fun `changeRole should reassign a resident to staff`() {
        val user = createUser(role = Role.RESIDENT)
        every { userRepository.findById(user.id) } returns user
        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userAdminService.changeRole(user.id, Role.STAFF)

        assertEquals(Role.STAFF, result.role)
        assertEquals(Role.STAFF, savedUserSlot.captured.role)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `changeRole should promote a resident to admin`() {
        val user = createUser(role = Role.RESIDENT)
        every { userRepository.findById(user.id) } returns user
        every { userRepository.save(any()) } answers { firstArg() }

        val result = userAdminService.changeRole(user.id, Role.ADMIN)

        assertEquals(Role.ADMIN, result.role)
    }

    @Test
    fun `changeRole should reject promoting to manager`() {
        val user = createUser(role = Role.RESIDENT)
        every { userRepository.findById(user.id) } returns user

        assertThrows<DomainValidationException> {
            userAdminService.changeRole(user.id, Role.MANAGER)
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `changeRole should reject demoting an existing manager`() {
        val user = createUser(role = Role.MANAGER)
        every { userRepository.findById(user.id) } returns user

        assertThrows<DomainValidationException> {
            userAdminService.changeRole(user.id, Role.RESIDENT)
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `changeRole should throw EntityNotFoundException when user does not exist`() {
        val userId = UserId.generate()
        every { userRepository.findById(userId) } returns null

        assertThrows<EntityNotFoundException> {
            userAdminService.changeRole(userId, Role.STAFF)
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ===== CHANGE SPECIALTY TESTS =====

    @Test
    fun `changeSpecialty should set the specialty and save the user`() {
        val user = createUser(role = Role.STAFF)
        every { userRepository.findById(user.id) } returns user
        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userAdminService.changeSpecialty(user.id, "Electrician")

        assertEquals("Electrician", result.specialty)
        assertEquals("Electrician", savedUserSlot.captured.specialty)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `changeSpecialty should clear the specialty when given null`() {
        val user = createUser(role = Role.STAFF).withSpecialty("Plumber")
        every { userRepository.findById(user.id) } returns user
        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userAdminService.changeSpecialty(user.id, null)

        assertNull(result.specialty)
        assertNull(savedUserSlot.captured.specialty)
    }

    @Test
    fun `changeSpecialty should throw EntityNotFoundException when user does not exist`() {
        val userId = UserId.generate()
        every { userRepository.findById(userId) } returns null

        assertThrows<EntityNotFoundException> {
            userAdminService.changeSpecialty(userId, "Electrician")
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }
}
