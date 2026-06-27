package com.sakena.user.infrastructure.web

data class AuthResponse(
    val token: String,
    val username: String,
    val role: String
)
