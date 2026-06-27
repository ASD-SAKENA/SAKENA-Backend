package com.sakena.user.infrastructure.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank
    val username: String,

    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 8)
    val password: String,

    val role: String? = null
)

data class LoginRequest(
    @field:NotBlank
    val username: String,

    @field:NotBlank
    val password: String
)
