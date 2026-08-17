package com.sakena.membership.infrastructure.web

import com.sakena.membership.application.InvitationService
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.membership.domain.model.InvitationId
import com.sakena.membership.infrastructure.web.dto.CreateInvitationRequest
import com.sakena.membership.infrastructure.web.dto.InvitationPreviewResponse
import com.sakena.membership.infrastructure.web.dto.InvitationResponse
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for building invitations. The manager issues and revokes them;
 * the invitee previews the link before signing in and accepts it afterwards.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitations", description = "Invite residents and staff into a building")
class InvitationController(
    private val invitationService: InvitationService,
    private val buildingRepository: BuildingRepository,
    private val apartmentRepository: ApartmentRepository,
    private val profileService: ProfileService,
) {

    @Operation(summary = "What a join link points at — readable before signing in")
    @GetMapping("/preview")
    fun preview(@RequestParam token: String): InvitationPreviewResponse {
        val invitation = invitationService.peek(token)
        val building = buildingRepository.findById(invitation.buildingId)
            ?: throw EntityNotFoundException("Building of this invitation no longer exists")
        val unitNumber = invitation.apartmentId
            ?.let { apartmentRepository.findById(it) }
            ?.unitNumber

        return InvitationPreviewResponse(
            buildingName = building.name,
            role = invitation.role,
            channel = invitation.channel,
            recipientHint = maskRecipient(invitation),
            unitNumber = unitNumber,
            expiresAt = invitation.expiresAt,
        )
    }

    @Operation(summary = "Accept an invitation as the signed-in user and join the building")
    @PostMapping("/accept")
    @SecurityRequirement(name = "bearerAuth")
    fun accept(@RequestParam token: String): InvitationResponse =
        toResponse(invitationService.accept(token, currentUser()))

    @Operation(summary = "Invitations issued for the building the requesting manager administers")
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    fun list(@RequestParam buildingId: String): List<InvitationResponse> =
        invitationService.getAll(BuildingId.from(buildingId), currentUser().managedBuildingId).map(::toResponse)

    @Operation(summary = "Invite someone into the building the requesting manager administers")
    @PostMapping("/buildings/{buildingId}")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    fun create(
        @PathVariable buildingId: String,
        @Valid @RequestBody request: CreateInvitationRequest,
    ): InvitationResponse {
        val manager = currentUser()
        return toResponse(
            invitationService.create(
                BuildingId.from(buildingId),
                request.toCommand(),
                manager.id,
                manager.managedBuildingId,
            ),
        )
    }

    @Operation(summary = "Revoke a pending invitation issued for the requesting manager's own building")
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    fun revoke(@PathVariable id: String): InvitationResponse =
        toResponse(invitationService.revoke(InvitationId.from(id), currentUser().managedBuildingId))

    private fun toResponse(invitation: BuildingInvitation): InvitationResponse =
        InvitationResponse.from(invitation, invitationService.acceptUrlOf(invitation))

    /**
     * Shows just enough for the invitee to recognise the invitation without
     * leaking the full address to anyone who happens to hold the link.
     */
    private fun maskRecipient(invitation: BuildingInvitation): String? {
        val recipient = invitation.recipient ?: return null
        return when (invitation.channel) {
            InvitationChannel.LINK -> null
            InvitationChannel.EMAIL -> {
                val name = recipient.substringBefore('@')
                val domain = recipient.substringAfter('@', "")
                "${name.take(2)}***@$domain"
            }
            InvitationChannel.PHONE -> "***${recipient.takeLast(4)}"
        }
    }

    private fun currentUser(): User {
        val username = SecurityContextHolder.getContext().authentication.name
        return profileService.getUserByUsername(username)
            ?: throw EntityNotFoundException("Signed-in user was not found")
    }
}
