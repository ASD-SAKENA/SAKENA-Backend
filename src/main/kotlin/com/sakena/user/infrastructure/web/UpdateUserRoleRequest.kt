package com.sakena.user.infrastructure.web

import jakarta.validation.constraints.NotBlank

data class UpdateUserRoleRequest(
    @field:NotBlank(message = "role must not be blank")
    val role: String
)
