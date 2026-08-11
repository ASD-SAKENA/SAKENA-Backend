package com.sakena.membership.domain

import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationId
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException

/**
 * Outbound port for persisting building invitations. Declared in the domain
 * layer and implemented by an adapter in infrastructure.
 */
interface InvitationRepository {
    fun save(invitation: BuildingInvitation): BuildingInvitation

    fun findById(id: InvitationId): BuildingInvitation?

    /** Looks an invitation up by the secret the recipient presents. */
    fun findByToken(token: String): BuildingInvitation?

    /** Invitations issued for a building, newest first. */
    fun findAllByBuilding(buildingId: BuildingId): List<BuildingInvitation>
}

class InvitationNotFoundException(id: InvitationId) :
    EntityNotFoundException("Invitation with id '$id' was not found")
