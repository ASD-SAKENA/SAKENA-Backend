package com.sakena.user.application

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class StaffDirectoryServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val service = StaffDirectoryService(userRepository)

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
        every { userRepository.findAll() } returns listOf(
            activeStaff,
            user(Role.STAFF, active = false),
            user(Role.RESIDENT, active = true),
            user(Role.MANAGER, active = true),
            user(Role.ADMIN, active = true),
        )

        val result = service.getActiveStaff()

        assertEquals(listOf(activeStaff), result)
    }
}
