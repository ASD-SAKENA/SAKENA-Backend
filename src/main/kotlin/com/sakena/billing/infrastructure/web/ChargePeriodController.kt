package com.sakena.billing.infrastructure.web

import com.sakena.billing.application.ChargePeriodService
import com.sakena.billing.application.InvoiceService
import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.infrastructure.web.dto.AddChargeItemRequest
import com.sakena.billing.infrastructure.web.dto.ChargeItemResponse
import com.sakena.billing.infrastructure.web.dto.ChargePeriodResponse
import com.sakena.billing.infrastructure.web.dto.CreateChargePeriodRequest
import com.sakena.billing.infrastructure.web.dto.UnitInvoiceResponse
import com.sakena.billing.infrastructure.web.dto.UpdateChargePeriodRequest
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
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
    fun list(@RequestParam(required = false) buildingId: String?): List<ChargePeriodResponse> =
        chargePeriodService.getAll(buildingId?.let { BuildingId.from(it) })
            .map(ChargePeriodResponse::from)

    @Operation(summary = "Get a charge period by id")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ChargePeriodResponse =
        ChargePeriodResponse.from(chargePeriodService.getById(ChargePeriodId.from(id)))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Define a new charge period for the building the requesting manager administers")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateChargePeriodRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<ChargePeriodResponse> {
        val period = chargePeriodService.create(request.toCommand(), currentUser().managedBuildingId)
        val location: URI = uriBuilder.path("/api/v1/charge-periods/{id}").build(period.id.value)
        return ResponseEntity.created(location).body(ChargePeriodResponse.from(period))
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Reschedule or rename a draft charge period (manager)")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateChargePeriodRequest,
    ): ChargePeriodResponse =
        ChargePeriodResponse.from(
            chargePeriodService.update(ChargePeriodId.from(id), request.toCommand(), currentUser().managedBuildingId),
        )

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a draft charge period (manager)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) =
        chargePeriodService.delete(ChargePeriodId.from(id), currentUser().managedBuildingId)

    @Operation(summary = "List a period's cost lines")
    @GetMapping("/{id}/items")
    fun listItems(@PathVariable id: String): List<ChargeItemResponse> =
        chargePeriodService.getItems(ChargePeriodId.from(id)).map(ChargeItemResponse::from)

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Add a cost line — recurring charge, facility cost or one-off expense (manager)")
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun addItem(
        @PathVariable id: String,
        @Valid @RequestBody request: AddChargeItemRequest,
    ): ChargeItemResponse =
        ChargeItemResponse.from(
            chargePeriodService.addItem(ChargePeriodId.from(id), request.toCommand(), currentUser().managedBuildingId),
        )

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Remove a cost line from a draft period (manager)")
    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeItem(@PathVariable id: String, @PathVariable itemId: String) =
        chargePeriodService.removeItem(ChargePeriodId.from(id), ChargeItemId.from(itemId), currentUser().managedBuildingId)

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Issue the period: allocate its cost lines into per-unit invoices (manager)")
    @PostMapping("/{id}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(@PathVariable id: String): List<UnitInvoiceResponse> =
        invoiceService.issue(ChargePeriodId.from(id), currentUser().managedBuildingId).map(UnitInvoiceResponse::from)

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Close a fully settled charge period (manager)")
    @PostMapping("/{id}/close")
    fun close(@PathVariable id: String): ChargePeriodResponse =
        ChargePeriodResponse.from(chargePeriodService.close(ChargePeriodId.from(id), currentUser().managedBuildingId))

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Payment status of every unit for this period (manager)")
    @GetMapping("/{id}/invoices")
    fun invoices(@PathVariable id: String): List<UnitInvoiceResponse> =
        invoiceService.getByPeriod(ChargePeriodId.from(id), currentUser().managedBuildingId).map(UnitInvoiceResponse::from)

    private fun currentUser(): User = SecurityContextHolder.getContext().authentication.name
        .let { username -> profileService.getUserByUsername(username) }
        ?: throw RuntimeException("User not found")
}
