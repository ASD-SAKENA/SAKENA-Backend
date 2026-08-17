package com.sakena.wallet.infrastructure.web

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.wallet.application.WalletService
import com.sakena.wallet.infrastructure.web.dto.FundWalletRequest
import com.sakena.wallet.infrastructure.web.dto.RecordBuildingTransactionRequest
import com.sakena.wallet.infrastructure.web.dto.WalletResponse
import com.sakena.wallet.infrastructure.web.dto.WalletTransactionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for personal wallets, the shared building account and wage settlement.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Personal wallets, building account and wage settlement")
@SecurityRequirement(name = "bearerAuth")
class WalletController(
    private val walletService: WalletService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Current user's wallet balance")
    @GetMapping("/me")
    fun myWallet(): WalletResponse =
        WalletResponse.from(walletService.getMyWallet(getCurrentUserId()))

    @Operation(summary = "Current user's wallet ledger, newest first")
    @GetMapping("/me/transactions")
    fun myTransactions(): List<WalletTransactionResponse> =
        walletService.getMyLedger(getCurrentUserId()).map(WalletTransactionResponse::from)

    @Operation(summary = "Fund the current resident's wallet (simulated top-up)")
    @PostMapping("/me/top-ups")
    @ResponseStatus(HttpStatus.CREATED)
    fun fundMyWallet(
        @Valid @RequestBody request: FundWalletRequest,
    ): WalletResponse =
        WalletResponse.from(walletService.fundMyWallet(request.toCommand(), getCurrentUserId()))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Balance of the building account the requesting manager administers")
    @GetMapping("/building")
    fun buildingWallet(): WalletResponse =
        WalletResponse.from(walletService.getBuildingWallet(currentUser().managedBuildingId))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Building account ledger — income and expenses, newest first")
    @GetMapping("/building/transactions")
    fun buildingTransactions(): List<WalletTransactionResponse> =
        walletService.getBuildingLedger(currentUser().managedBuildingId).map(WalletTransactionResponse::from)

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Record income or an expense on the building account")
    @PostMapping("/building/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun recordBuildingTransaction(
        @Valid @RequestBody request: RecordBuildingTransactionRequest,
    ): WalletResponse =
        WalletResponse.from(
            walletService.recordBuildingTransaction(request.toCommand(), currentUser().managedBuildingId),
        )

    @Operation(summary = "Settle a completed service request's wage (manager)")
    @PostMapping("/settle/{serviceRequestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun settle(@PathVariable serviceRequestId: String) {
        walletService.settleServiceRequest(
            ServiceRequestId.fromString(serviceRequestId),
            getCurrentUserId(),
        )
    }

    private fun currentUser(): User = SecurityContextHolder.getContext().authentication.name
        .let { username -> profileService.getUserByUsername(username) }
        ?: throw RuntimeException("User not found")

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }
}
