package com.sakena.membership.application.command

import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.Role

data class CreateInvitationCommand(
    val channel: InvitationChannel,
    /** Email or phone; ignored for an open link. */
    val recipient: String?,
    val role: Role,
    val apartmentId: ApartmentId?,
    val tenancy: TenancyType?,
)
