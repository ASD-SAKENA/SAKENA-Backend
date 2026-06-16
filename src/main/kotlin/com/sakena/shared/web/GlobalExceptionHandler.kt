package com.sakena.shared.web

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates exceptions into the uniform [ApiError] payload. Centralising this
 * keeps controllers thin and the error contract consistent across the API.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException, request: HttpServletRequest) =
        build(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found", request)

    @ExceptionHandler(DomainValidationException::class)
    fun handleDomainValidation(ex: DomainValidationException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(DomainConflictException::class)
    fun handleConflict(ex: DomainConflictException, request: HttpServletRequest) =
        build(HttpStatus.CONFLICT, ex.message ?: "Conflict", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val fieldErrors = ex.bindingResult.fieldErrors.map {
            ApiError.FieldError(it.field, it.defaultMessage ?: "is invalid")
        }
        val body = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Request validation failed",
            path = request.requestURI,
            fieldErrors = fieldErrors,
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        log.error("Unhandled exception for {} {}", request.method, request.requestURI, ex)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request)
    }

    private fun build(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(
            ApiError(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
            ),
        )
}
