package com.sakena.shared.web

import java.time.Instant

/**
 * Uniform error payload returned by [GlobalExceptionHandler] for every failed request.
 */
data class ApiError(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now(),
    val fieldErrors: List<FieldError> = emptyList(),
) {
    data class FieldError(
        val field: String,
        val message: String,
    )
}
