package com.sakena.membership.infrastructure.web.dto

import com.sakena.membership.application.BuildingMember
import com.sakena.membership.application.command.CreateInvitationCommand
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.membership.domain.model.InvitationStatus
import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.Role
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateInvitationRequest(
    @field:NotNull(message = "channel must not be null")
    val channel: InvitationChannel,

    /** Email or phone number; ignored for an open link. */
    val recipient: String? = null,

    @field:NotNull(message = "role must not be null")
    val role: Role,

    /** Unit the invitee moves into on acceptance; omit to leave them unassigned. */
    val apartmentId: UUID? = null,

    val tenancy: TenancyType? = null,
) {
    fun toCommand() = CreateInvitationCommand(
        channel = channel,
        recipient = recipient,
        role = role,
        apartmentId = apartmentId?.let(::ApartmentId),
        tenancy = tenancy,
    )
}

/**
 * The manager's view of an invitation, including the shareable link so phone
 * and open-link invitations can be copied straight out of the UI.
 */
data class InvitationResponse(
    val id: UUID,
    val buildingId: UUID,
    val channel: InvitationChannel,
    val recipient: String?,
    val role: Role,
    val apartmentId: UUID?,
    val tenancy: TenancyType?,
    val status: InvitationStatus,
    val acceptUrl: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
) {
    companion object {
        fun from(invitation: BuildingInvitation, acceptUrl: String) = InvitationResponse(
            id = invitation.id.value,
            buildingId = invitation.buildingId.value,
            channel = invitation.channel,
            recipient = invitation.recipient,
            role = invitation.role,
            apartmentId = invitation.apartmentId?.value,
            tenancy = invitation.tenancy,
            status = invitation.status,
            acceptUrl = acceptUrl,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
            acceptedAt = invitation.acceptedAt,
        )
    }
}

/**
 * What the join screen shows before the invitee signs in. Deliberately minimal
 * — anyone holding the link can read it, so it exposes no personal data.
 */
data class InvitationPreviewResponse(
    val buildingName: String,
    val role: Role,
    val channel: InvitationChannel,
    /** Masked, so the link alone never reveals the full address. */
    val recipientHint: String?,
    val unitNumber: String?,
    val expiresAt: Instant,
)

/**
 * A member of the building as the manager's units screen renders them. A null
 * [unitNumber] is the actionable case: they joined but still need a unit.
 */
data class BuildingMemberResponse(
    val userId: String,
    val username: String,
    val email: String,
    val role: Role,
    val unitNumber: String?,
    val tenancy: TenancyType?,
) {
    companion object {
        fun from(member: BuildingMember) = BuildingMemberResponse(
            userId = member.user.id.value.toString(),
            username = member.user.username,
            email = member.user.email,
            role = member.user.role,
            unitNumber = member.unitNumber,
            tenancy = member.tenancy,
        )
    }
}
