package com.sakena.billing.infrastructure.web

import com.sakena.billing.application.InvoiceService
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.billing.infrastructure.web.dto.PayInvoiceFromWalletRequest
import com.sakena.billing.infrastructure.web.dto.RegisterInvoicePaymentRequest
import com.sakena.billing.infrastructure.web.dto.UnitInvoiceResponse
import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

/**
 * REST adapter for issued invoices: the manager's building-wide payment
 * status and a unit's own bill history.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Per-unit charge invoices and their settlement")
@SecurityRequirement(name = "bearerAuth")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Signed-in resident's invoices for their current unit")
    @GetMapping("/mine")
    fun mine(principal: Principal): List<UnitInvoiceResponse> {
        val user = currentUser(principal)
        if (user.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can list their own invoices")
        }
        return invoiceService.getMine(user.id).map(::toResponse)
    }

    @Operation(
        summary = "Outstanding invoices across issued periods (manager), optional period filter",
    )
    @GetMapping("/outstanding")
    fun outstanding(
        @RequestParam(required = false) periodId: String?,
        principal: Principal,
    ): List<UnitInvoiceResponse> {
        val user = currentUser(principal)
        if (user.role != Role.MANAGER) {
            throw DomainForbiddenException("Only managers can list outstanding invoices")
        }
        return invoiceService.getOutstanding(
            user.id,
            periodId?.let(ChargePeriodId::from),
        ).map(UnitInvoiceResponse::from)
    }

    @Operation(summary = "Invoices of a single unit, newest first")
    @GetMapping
    fun listByApartment(
        @RequestParam apartmentId: String,
        principal: Principal,
    ): List<UnitInvoiceResponse> {
        val id = ApartmentId.from(apartmentId)
        val user = currentUser(principal)
        val invoices = when (user.role) {
            Role.MANAGER -> invoiceService.getByApartment(id, user.id)
            Role.RESIDENT -> invoiceService.getOwnApartment(id, user.id)
            Role.STAFF, Role.ADMIN -> throw DomainForbiddenException("You may not access apartment invoices")
        }
        return invoices.map(::toResponse)
    }

    @Operation(summary = "Register a payment against an invoice (manager)")
    @PostMapping("/{id}/payments")
    fun registerPayment(
        @PathVariable id: String,
        @Valid @RequestBody request: RegisterInvoicePaymentRequest,
        principal: Principal,
    ): UnitInvoiceResponse =
        toResponse(
            invoiceService.registerPayment(
                UnitInvoiceId.from(id),
                request.toCommand(),
                currentUser(principal).id,
            ),
        )

    @Operation(summary = "Pay an outstanding invoice from the resident wallet (instant)")
    @PostMapping("/{id}/pay-from-wallet")
    fun payFromWallet(
        @PathVariable id: String,
        @Valid @RequestBody(required = false) request: PayInvoiceFromWalletRequest?,
        principal: Principal,
    ): UnitInvoiceResponse {
        val user = currentUser(principal)
        if (user.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can pay from their wallet")
        }
        return toResponse(
            invoiceService.payFromWallet(
                UnitInvoiceId.from(id),
                user.id,
                request?.amount,
            ),
        )
    }

    private fun toResponse(invoice: UnitInvoice): UnitInvoiceResponse =
        UnitInvoiceResponse.from(invoiceService.detailsOf(invoice))

    private fun currentUser(principal: Principal): User =
        profileService.getUserByUsername(principal.name)
            ?: throw EntityNotFoundException("Signed-in user was not found")
}
