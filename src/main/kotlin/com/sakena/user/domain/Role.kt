package com.sakena.user.domain

import com.sakena.user.domain.exceptions.InvalidRoleException

enum class Role {
    RESIDENT,
    MANAGER,
    STAFF,
    ADMIN;

    companion object {
        fun from(value: String): Role =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw InvalidRoleException(value)
    }
}
