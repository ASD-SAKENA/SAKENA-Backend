package com.sakena.servicerequest.infrastructure.web

import jakarta.validation.constraints.NotBlank

data class CreateServiceRequestRequest(
    @field:NotBlank
    val title: String,

    @field:NotBlank
    val description: String,

    val location: String? = null
)
