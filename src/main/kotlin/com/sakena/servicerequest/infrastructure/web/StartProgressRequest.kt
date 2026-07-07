package com.sakena.servicerequest.infrastructure.web

import java.time.Instant

data class StartProgressRequest(
    val expectedCompletionAt: Instant? = null
)
