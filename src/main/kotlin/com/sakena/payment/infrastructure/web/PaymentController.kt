package com.sakena.payment.infrastructure.web

import com.sakena.payment.application.PaymentService
import com.sakena.payment.infrastructure.web.dto.PaymentResponse
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for the Payment bounded context. Controllers stay thin:
 * parse/validate input, delegate to the application service, map the result
 * back to a DTO.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Record payments and read payment history")
@SecurityRequirement(name = "bearerAuth")
class PaymentController(
    private val paymentService: PaymentService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Current user's payment history, newest first")
    @GetMapping
    fun history(): List<PaymentResponse> =
        paymentService.getHistory(getCurrentUserId()).map(PaymentResponse::from)

    @Operation(summary = "Record a payment made by the current user")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun record(@Valid @RequestBody request: RecordPaymentRequest): PaymentResponse {
        val payment = paymentService.record(request.toCommand(), getCurrentUserId())
        return PaymentResponse.from(payment)
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }
}
