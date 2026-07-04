package com.sakena.user.infrastructure.web

data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null
)
