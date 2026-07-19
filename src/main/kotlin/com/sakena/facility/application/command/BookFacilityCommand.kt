package com.sakena.facility.application.command

import java.time.Instant

data class BookFacilityCommand(
    val startsAt: Instant,
    val endsAt: Instant,
)
