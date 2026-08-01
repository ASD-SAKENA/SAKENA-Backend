package com.sakena.payment.infrastructure.web

import com.sakena.payment.application.PaymentService
import com.sakena.payment.domain.model.PaymentId
import com.sakena.payment.infrastructure.web.dto.PaymentResponse
import com.sakena.payment.infrastructure.web.dto.RecordPaymentRequest
import com.sakena.payment.infrastructure.web.dto.RejectPaymentRequest
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Resident payment submissions and manager review")
@SecurityRequirement(name = "bearerAuth")
class PaymentController(
    private val paymentService: PaymentService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Submit payment evidence for manager review (resident)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@Valid @RequestBody request: RecordPaymentRequest): PaymentResponse =
        PaymentResponse.from(paymentService.submit(request.toCommand(), getCurrentUserId()))

    @Operation(summary = "Current resident's confirmed payment history, newest first")
    @GetMapping
    fun history(): List<PaymentResponse> =
        paymentService.getHistory(getCurrentUserId()).map(PaymentResponse::from)

    @Operation(summary = "Current resident's submissions in every status, newest first")
    @GetMapping("/submissions")
    fun submissions(): List<PaymentResponse> =
        paymentService.getSubmissions(getCurrentUserId()).map(PaymentResponse::from)

    @Operation(summary = "Pending payment review queue, newest first (manager)")
    @GetMapping("/pending")
    fun pending(): List<PaymentResponse> =
        paymentService.getPending(getCurrentUserId()).map(PaymentResponse::from)

    @Operation(summary = "Confirm a pending payment (manager)")
    @PatchMapping("/{id}/confirm")
    fun confirm(@PathVariable id: String): PaymentResponse =
        PaymentResponse.from(
            paymentService.confirm(PaymentId.from(id), getCurrentUserId()),
        )

    @Operation(summary = "Reject a pending payment (manager)")
    @PatchMapping("/{id}/reject")
    fun reject(
        @PathVariable id: String,
        @Valid @RequestBody request: RejectPaymentRequest,
    ): PaymentResponse =
        PaymentResponse.from(
            paymentService.reject(PaymentId.from(id), getCurrentUserId(), request.reason),
        )

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw IllegalStateException("Authenticated user '$username' no longer exists")
        return user.id
    }
}
