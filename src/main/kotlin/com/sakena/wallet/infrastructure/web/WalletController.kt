package com.sakena.wallet.infrastructure.web

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import com.sakena.wallet.application.WalletService
import com.sakena.wallet.infrastructure.web.dto.WalletResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for the Wallet bounded context: the worker's own balance and
 * the manager's wage settlement of a completed service request.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Worker wallets and wage settlement")
@SecurityRequirement(name = "bearerAuth")
class WalletController(
    private val walletService: WalletService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Current user's wallet balance")
    @GetMapping("/me")
    fun myWallet(): WalletResponse =
        WalletResponse.from(walletService.getMyWallet(getCurrentUserId()))

    @Operation(summary = "Settle a completed service request's wage (manager)")
    @PostMapping("/settle/{serviceRequestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun settle(@PathVariable serviceRequestId: String) {
        walletService.settleServiceRequest(
            ServiceRequestId.fromString(serviceRequestId),
            getCurrentUserId(),
        )
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }
}
