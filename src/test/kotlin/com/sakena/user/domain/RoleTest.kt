package com.sakena.user.domain

import com.sakena.user.domain.exceptions.InvalidRoleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoleTest {

    @Test
    fun `from parses a role without depending on case or surrounding whitespace`() {
        assertEquals(Role.RESIDENT, Role.from(" resident "))
    }

    @Test
    fun `from rejects an unsupported role`() {
        val exception = assertThrows<InvalidRoleException> { Role.from("OWNER") }

        assertEquals("Invalid role 'OWNER'", exception.message)
    }
}
