package com.sakena.user.domain

import java.util.UUID

@JvmInline
value class UserId(val value: UUID) {
    companion object {
        fun generate() = UserId(UUID.randomUUID())
        fun fromString(id: String) = UserId(UUID.fromString(id))
    }
}
