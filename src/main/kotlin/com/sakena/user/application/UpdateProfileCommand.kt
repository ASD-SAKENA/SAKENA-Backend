package com.sakena.user.application

data class UpdateProfileCommand(
    val username: String? = null,
    val email: String? = null
)
