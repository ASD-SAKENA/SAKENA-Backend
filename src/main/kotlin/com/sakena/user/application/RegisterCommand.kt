package com.sakena.user.application

data class RegisterCommand(
    val username: String,
    val email: String,
    val password: String,
    val role: String? = null
)
