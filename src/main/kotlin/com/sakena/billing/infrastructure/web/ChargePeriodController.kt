package com.sakena.billing.infrastructure.web

import com.sakena.billing.application.ChargePeriodService
import com.sakena.billing.application.InvoiceService
import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.infrastructure.web.dto.AddChargeItemRequest
import com.sakena.billing.infrastructure.web.dto.ChargeItemResponse
import com.sakena.billing.infrastructure.web.dto.ChargePeriodResponse
import com.sakena.billing.infrastructure.web.dto.CreateChargePeriodRequest
import com.sakena.billing.infrastructure.web.dto.ServiceChargeResponse
import com.sakena.billing.infrastructure.web.dto.UnitInvoiceResponse
import com.sakena.billing.infrastructure.web.dto.UpdateChargePeriodRequest
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.Principal

/**
 * REST adapter for charge periods and their cost lines. Controllers stay thin:
 * parse/validate input, delegate to the application service, map the result
 * back to a DTO.
 */
@RestController
@RequestMapping("/api/v1/charge-periods")
@Tag(name = "Charge Periods", description = "Define billing periods, their cost lines and issue invoices")
@SecurityRequirement(name = "bearerAuth")
class ChargePeriodController(
    private val chargePeriodService: ChargePeriodService,
    private val invoiceService: InvoiceService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List charge periods, newest first")
    @GetMapping
    fun list(
        @RequestParam(required = false) buildingId: String?,
        principal: Principal,
    ): List<ChargePeriodResponse> =
        chargePeriodService.getAll(
            buildingId?.let { BuildingId.from(it) },
            currentManagerId(principal),
        )
            .map(ChargePeriodResponse::from)

    @Operation(
        summary = "Pending service costs waiting to attach on the next period issue (manager)",
    )
    @GetMapping("/pending-service-charges")
    fun pendingServiceCharges(principal: Principal): List<ServiceChargeResponse> =
        chargePeriodService.getPendingServiceCharges(currentManagerId(principal))
            .map(ServiceChargeResponse::from)

    @Operation(summary = "Get a charge period by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String, principal: Principal): ChargePeriodResponse =
        ChargePeriodResponse.from(
            chargePeriodService.getById(ChargePeriodId.from(id), currentManagerId(principal)),
        )

    @Operation(summary = "Define a new charge period (manager)")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateChargePeriodRequest,
        uriBuilder: UriComponentsBuilder,
        principal: Principal,
    ): ResponseEntity<ChargePeriodResponse> {
        val period = chargePeriodService.create(request.toCommand(), currentManagerId(principal))
        val location: URI = uriBuilder.path("/api/v1/charge-periods/{id}").build(period.id.value)
        return ResponseEntity.created(location).body(ChargePeriodResponse.from(period))
    }

    @Operation(summary = "Reschedule or rename a draft charge period (manager)")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateChargePeriodRequest,
        principal: Principal,
    ): ChargePeriodResponse =
        ChargePeriodResponse.from(
            chargePeriodService.update(
                ChargePeriodId.from(id),
                request.toCommand(),
                currentManagerId(principal),
            ),
        )

    @Operation(summary = "Delete a draft charge period (manager)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String, principal: Principal) =
        chargePeriodService.delete(ChargePeriodId.from(id), currentManagerId(principal))

    @Operation(summary = "List a period's cost lines")
    @GetMapping("/{id}/items")
    fun listItems(@PathVariable id: String, principal: Principal): List<ChargeItemResponse> =
        chargePeriodService.getItems(ChargePeriodId.from(id), currentManagerId(principal))
            .map(ChargeItemResponse::from)

    @Operation(summary = "Add a cost line — recurring charge, facility cost or one-off expense (manager)")
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun addItem(
        @PathVariable id: String,
        @Valid @RequestBody request: AddChargeItemRequest,
        principal: Principal,
    ): ChargeItemResponse =
        ChargeItemResponse.from(
            chargePeriodService.addItem(
                ChargePeriodId.from(id),
                request.toCommand(),
                currentManagerId(principal),
            ),
        )

    @Operation(summary = "Remove a cost line from a draft period (manager)")
    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeItem(
        @PathVariable id: String,
        @PathVariable itemId: String,
        principal: Principal,
    ) = chargePeriodService.removeItem(
        ChargePeriodId.from(id),
        ChargeItemId.from(itemId),
        currentManagerId(principal),
    )

    @Operation(summary = "Issue the period: allocate its cost lines into per-unit invoices (manager)")
    @PostMapping("/{id}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(@PathVariable id: String, principal: Principal): List<UnitInvoiceResponse> =
        invoiceService.issue(ChargePeriodId.from(id), currentManagerId(principal))
            .map { invoice ->
                val period = invoiceService.periodOf(invoice)
                UnitInvoiceResponse.from(
                    invoice = invoice,
                    periodTitle = period?.title.orEmpty(),
                    startsOn = period?.startsOn,
                    endsOn = period?.endsOn,
                )
            }

    @Operation(summary = "Close a fully settled charge period (manager)")
    @PostMapping("/{id}/close")
    fun close(@PathVariable id: String, principal: Principal): ChargePeriodResponse =
        ChargePeriodResponse.from(
            chargePeriodService.close(ChargePeriodId.from(id), currentManagerId(principal)),
        )

    @Operation(summary = "Payment status of every unit for this period (manager)")
    @GetMapping("/{id}/invoices")
    fun invoices(@PathVariable id: String, principal: Principal): List<UnitInvoiceResponse> =
        invoiceService.getByPeriod(ChargePeriodId.from(id), currentManagerId(principal))
            .map { invoice ->
                val period = invoiceService.periodOf(invoice)
                UnitInvoiceResponse.from(
                    invoice = invoice,
                    periodTitle = period?.title.orEmpty(),
                    startsOn = period?.startsOn,
                    endsOn = period?.endsOn,
                )
            }

    private fun currentManagerId(principal: Principal): UserId =
        profileService.getUserByUsername(principal.name)?.id
            ?: throw EntityNotFoundException("Signed-in manager was not found")
}
