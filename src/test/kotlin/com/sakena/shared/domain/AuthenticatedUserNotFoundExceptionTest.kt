package com.sakena.shared.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class AuthenticatedUserNotFoundExceptionTest {

    @Test
    fun `a stale credential is unauthorized, not a server fault`() {
        // Controllers used to throw a bare RuntimeException here, which the
        // catch-all handler turned into a 500 — telling the caller the server
        // broke when in fact their token names a user that no longer exists.
        val exception = AuthenticatedUserNotFoundException()

        assertTrue(
            exception is DomainUnauthorizedException,
            "must map to 401, not the generic 500 handler",
        )
    }
}
