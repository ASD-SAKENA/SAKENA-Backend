package com.sakena.billing.infrastructure.web

import com.sakena.billing.application.InvoiceService
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.billing.infrastructure.web.dto.RegisterInvoicePaymentRequest
import com.sakena.billing.infrastructure.web.dto.UnitInvoiceResponse
import com.sakena.property.domain.model.ApartmentId
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
) {

    @Operation(summary = "Invoices of a single unit, newest first")
    @GetMapping
    fun listByApartment(@RequestParam apartmentId: String): List<UnitInvoiceResponse> =
        invoiceService.getByApartment(ApartmentId.from(apartmentId)).map(UnitInvoiceResponse::from)

    @Operation(summary = "Register a payment against an invoice (manager)")
    @PostMapping("/{id}/payments")
    fun registerPayment(
        @PathVariable id: String,
        @Valid @RequestBody request: RegisterInvoicePaymentRequest,
    ): UnitInvoiceResponse =
        UnitInvoiceResponse.from(
            invoiceService.registerPayment(UnitInvoiceId.from(id), request.toCommand()),
        )
}
