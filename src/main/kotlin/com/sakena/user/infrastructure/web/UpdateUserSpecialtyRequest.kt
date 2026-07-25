package com.sakena.user.infrastructure.web

import jakarta.validation.constraints.Size

data class UpdateUserSpecialtyRequest(
    @field:Size(max = 100, message = "specialty must be at most 100 characters")
    val specialty: String? = null
)
