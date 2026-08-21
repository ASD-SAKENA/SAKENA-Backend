package com.sakena.support.infrastructure.web

import com.sakena.shared.domain.AuthenticatedUserNotFoundException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.support.application.SupportTicketService
import com.sakena.support.application.command.TicketAttachmentUpload
import com.sakena.support.domain.model.TicketId
import com.sakena.support.domain.model.TicketMessageKind
import com.sakena.support.domain.model.TicketStatus
import com.sakena.support.infrastructure.web.dto.OpenTicketRequest
import com.sakena.support.infrastructure.web.dto.ReplyRequest
import com.sakena.support.infrastructure.web.dto.TicketAttachmentResponse
import com.sakena.support.infrastructure.web.dto.TicketMessageResponse
import com.sakena.support.infrastructure.web.dto.TicketResponse
import com.sakena.support.infrastructure.web.dto.TicketThreadResponse
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Private complaint, criticism and suggestion threads between a resident and
 * their building manager. Staff are rejected by the application service.
 */
@RestController
@RequestMapping("/api/v1/support-tickets")
@Tag(name = "Support Tickets", description = "Private resident-to-manager tickets with image and voice attachments")
@SecurityRequirement(name = "bearerAuth")
class SupportTicketController(
    private val service: SupportTicketService,
    private val assembler: TicketAssembler,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Open a ticket (resident)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun open(@Valid @RequestBody request: OpenTicketRequest): TicketResponse {
        val viewer = currentUser()
        return assembler.toResponse(service.open(request.toCommand(), viewer), viewer)
    }

    @Operation(summary = "The signed-in resident's own tickets, freshest first")
    @GetMapping("/mine")
    fun mine(): List<TicketResponse> {
        val viewer = currentUser()
        return service.getMine(viewer).map { assembler.toResponse(it, viewer) }
    }

    @Operation(summary = "The managed building's tickets, optionally filtered by status (manager)")
    @GetMapping
    fun forBuilding(
        @RequestParam(required = false) status: TicketStatus?,
    ): List<TicketResponse> {
        val viewer = currentUser()
        return service.getForBuilding(status, viewer).map { assembler.toResponse(it, viewer) }
    }

    @Operation(summary = "A ticket with its whole thread")
    @GetMapping("/{ticketId}")
    fun thread(@PathVariable ticketId: String): TicketThreadResponse {
        val viewer = currentUser()
        val (ticket, messages) = service.getThread(TicketId.from(ticketId), viewer)
        return assembler.toThread(ticket, messages, viewer)
    }

    @Operation(summary = "Reply in the thread — text, or a previously uploaded attachment")
    @PostMapping("/{ticketId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    fun reply(
        @PathVariable ticketId: String,
        @Valid @RequestBody request: ReplyRequest,
    ): TicketMessageResponse {
        val viewer = currentUser()
        val id = TicketId.from(ticketId)
        val message = service.reply(id, request.toCommand(), viewer)
        val (ticket, _) = service.getThread(id, viewer)
        return assembler.toMessage(message, ticket, viewer)
    }

    @Operation(summary = "Upload an image or voice note, then reference its key in a reply")
    @PostMapping("/{ticketId}/attachments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadAttachment(
        @PathVariable ticketId: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam kind: TicketMessageKind,
    ): TicketAttachmentResponse {
        if (file.isEmpty) throw DomainValidationException("The uploaded file is empty")
        val contentType = file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        val storageKey = file.inputStream.use { stream ->
            service.uploadAttachment(
                ticketId = TicketId.from(ticketId),
                kind = kind,
                upload = TicketAttachmentUpload(
                    originalFilename = file.originalFilename,
                    contentType = contentType,
                    sizeBytes = file.size,
                    content = stream,
                ),
                requestedBy = currentUser(),
            )
        }
        return TicketAttachmentResponse(storageKey, contentType, file.size)
    }

    @Operation(summary = "Mark the ticket answered (manager)")
    @PatchMapping("/{ticketId}/answer")
    fun markAnswered(@PathVariable ticketId: String): TicketResponse {
        val viewer = currentUser()
        return assembler.toResponse(service.markAnswered(TicketId.from(ticketId), viewer), viewer)
    }

    private fun currentUser(): User {
        val username = SecurityContextHolder.getContext().authentication.name
        return profileService.getUserByUsername(username)
            ?: throw AuthenticatedUserNotFoundException()
    }
}
