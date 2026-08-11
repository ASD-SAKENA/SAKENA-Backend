package com.sakena.user.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sakena.shared.web.ApiError
import com.sakena.shared.web.ratelimit.FixedWindowRateLimiter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Throttles the unauthenticated auth endpoints per client IP. Without it,
 * login and password-reset are an open door for credential stuffing and for
 * flooding someone's inbox with reset mails.
 *
 * A caller that authenticates successfully gets their counter cleared, so a
 * legitimate user who mistyped a password a few times is not punished.
 */
@Component
class AuthRateLimitFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${app.rate-limit.auth.attempts:10}")
    attempts: Int,
    @Value("\${app.rate-limit.auth.window-seconds:60}")
    windowSeconds: Long,
) : OncePerRequestFilter() {

    private val limiter = FixedWindowRateLimiter(attempts, Duration.ofSeconds(windowSeconds))

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || request.requestURI !in THROTTLED_PATHS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = clientIpOf(request)
        if (!limiter.tryAcquire(key)) {
            writeTooManyRequests(request, response, limiter.retryAfterSeconds(key))
            return
        }

        filterChain.doFilter(request, response)

        // Register returns 201 Created (not 200). Treat both as successful auth
        // so a legitimate signup clears the window the same way a login does.
        if (response.status == HttpStatus.OK.value() ||
            response.status == HttpStatus.CREATED.value()
        ) {
            limiter.reset(key)
        }
    }

    /** Test-only: wipe in-memory windows so one class cannot starve another. */
    fun clear() {
        limiter.clear()
    }

    private fun writeTooManyRequests(
        request: HttpServletRequest,
        response: HttpServletResponse,
        retryAfterSeconds: Long,
    ) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        objectMapper.writeValue(
            response.outputStream,
            ApiError(
                status = HttpStatus.TOO_MANY_REQUESTS.value(),
                error = HttpStatus.TOO_MANY_REQUESTS.reasonPhrase,
                message = "Too many attempts. Try again in $retryAfterSeconds seconds.",
                path = request.requestURI,
            ),
        )
    }

    /**
     * Behind the reverse proxy the socket address is the proxy's, so the first
     * hop in X-Forwarded-For is the real client when the header is present.
     */
    private fun clientIpOf(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.substringBefore(',').trim()
        }
        return request.remoteAddr ?: "unknown"
    }

    private companion object {
        val THROTTLED_PATHS = setOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
        )
    }
}
