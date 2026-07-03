package com.sakena.user.infrastructure.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank
    val token: String,

    @field:NotBlank
    @field:Size(min = 8)
    val newPassword: String
)
