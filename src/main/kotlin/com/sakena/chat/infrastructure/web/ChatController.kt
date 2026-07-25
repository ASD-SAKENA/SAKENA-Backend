package com.sakena.chat.infrastructure.web

import com.sakena.chat.application.ChatService
import com.sakena.chat.application.command.SendAttachmentCommand
import com.sakena.chat.domain.model.ChatMessageId
import com.sakena.chat.domain.model.ChatMessageKind
import com.sakena.chat.infrastructure.web.dto.ChatMessageResponse
import com.sakena.chat.infrastructure.web.dto.EditMessageRequest
import com.sakena.chat.infrastructure.web.dto.SendMessageRequest
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
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
import java.time.Instant

/**
 * REST adapter for a building's group chat: history paging, a polling tail for
 * live updates, text and attachment messages, plus edit and delete.
 */
@RestController
@RequestMapping("/api/v1/buildings/{buildingId}/chat/messages")
@Tag(name = "Building Chat", description = "Group chat of a building, with image and voice attachments")
@SecurityRequirement(name = "bearerAuth")
class ChatController(
    private val chatService: ChatService,
    private val assembler: ChatMessageAssembler,
    private val profileService: ProfileService,
) {

    @Operation(summary = "Message history, oldest-first; page backwards with `before`")
    @GetMapping
    fun list(
        @PathVariable buildingId: String,
        @RequestParam(required = false) before: Instant?,
        @RequestParam(required = false) limit: Int?,
    ): List<ChatMessageResponse> {
        val viewer = currentUser()
        val messages = chatService.getPage(BuildingId.from(buildingId), before, limit)
        return assembler.toResponses(messages, viewer.id)
    }

    @Operation(summary = "Messages sent after a timestamp — the client's live tail")
    @GetMapping("/since")
    fun since(
        @PathVariable buildingId: String,
        @RequestParam since: Instant,
    ): List<ChatMessageResponse> {
        val viewer = currentUser()
        val messages = chatService.getSince(BuildingId.from(buildingId), since)
        return assembler.toResponses(messages, viewer.id)
    }

    @Operation(summary = "Send a text message")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun send(
        @PathVariable buildingId: String,
        @Valid @RequestBody request: SendMessageRequest,
    ): ChatMessageResponse {
        val sender = currentUser()
        val message = chatService.sendText(BuildingId.from(buildingId), request.toCommand(), sender)
        return assembler.toResponse(message, sender.id)
    }

    @Operation(summary = "Send an image or a voice note stored in object storage")
    @PostMapping("/attachments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun sendAttachment(
        @PathVariable buildingId: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam kind: ChatMessageKind,
        @RequestParam(required = false) caption: String?,
        @RequestParam(required = false) durationSeconds: Int?,
    ): ChatMessageResponse {
        if (file.isEmpty) throw DomainValidationException("The uploaded file is empty")
        val sender = currentUser()
        val command = SendAttachmentCommand(
            kind = kind,
            originalFilename = file.originalFilename,
            contentType = file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
            sizeBytes = file.size,
            content = file.inputStream,
            caption = caption,
            durationSeconds = durationSeconds,
        )
        val message = chatService.sendAttachment(BuildingId.from(buildingId), command, sender)
        return assembler.toResponse(message, sender.id)
    }

    @Operation(summary = "Edit your own text message; the response is marked as edited")
    @PatchMapping("/{messageId}")
    fun edit(
        @PathVariable buildingId: String,
        @PathVariable messageId: String,
        @Valid @RequestBody request: EditMessageRequest,
    ): ChatMessageResponse {
        val editor = currentUser()
        val message = chatService.edit(ChatMessageId.from(messageId), request.toCommand(), editor)
        return assembler.toResponse(message, editor.id)
    }

    @Operation(summary = "Delete your own message — the building manager may delete any message")
    @DeleteMapping("/{messageId}")
    fun delete(
        @PathVariable buildingId: String,
        @PathVariable messageId: String,
    ): ChatMessageResponse {
        val requester = currentUser()
        val message = chatService.delete(ChatMessageId.from(messageId), requester)
        return assembler.toResponse(message, requester.id)
    }

    private fun currentUser(): User {
        val username = SecurityContextHolder.getContext().authentication.name
        return profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
    }
}
