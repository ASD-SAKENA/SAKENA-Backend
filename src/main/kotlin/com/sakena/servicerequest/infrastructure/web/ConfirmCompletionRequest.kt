package com.sakena.servicerequest.infrastructure.web

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class ConfirmCompletionRequest(
    @field:NotNull(message = "score is required")
    @field:Min(1, message = "score must be at least 1")
    @field:Max(5, message = "score must be at most 5")
    val score: Int? = null,
)
