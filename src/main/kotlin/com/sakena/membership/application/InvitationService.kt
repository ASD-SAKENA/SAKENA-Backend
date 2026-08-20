package com.sakena.membership.application

import com.sakena.membership.application.command.CreateInvitationCommand
import com.sakena.membership.domain.InvitationNotFoundException
import com.sakena.membership.domain.InvitationNotifier
import com.sakena.membership.domain.InvitationRepository
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationId
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.ResidencyService
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Application service for inviting people into a building.
 *
 * An invitation carries everything the join needs — the building, the role and
 * optionally the unit — so accepting it is a single step for the invitee, and
 * the manager never has to assign the unit afterwards.
 */
@Service
@Transactional
class InvitationService(
    private val invitationRepository: InvitationRepository,
    private val buildingRepository: BuildingRepository,
    private val apartmentRepository: ApartmentRepository,
    private val residencyService: ResidencyService,
    private val notifier: InvitationNotifier,
    @Value("\${app.frontend-url:http://localhost:3000}")
    private val frontendUrl: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(
        buildingId: BuildingId,
        command: CreateInvitationCommand,
        invitedBy: UserId,
        requesterManagedBuildingId: BuildingId?,
    ): BuildingInvitation {
        val building = buildingRepository.findById(buildingId)
            ?: throw EntityNotFoundException("Building with id '$buildingId' was not found")
        if (requesterManagedBuildingId != buildingId) {
            throw DomainForbiddenException("You do not manage building '$buildingId'")
        }
        command.apartmentId?.let { apartmentId ->
            val apartment = apartmentRepository.findById(apartmentId)
                ?: throw EntityNotFoundException("Apartment with id '$apartmentId' was not found")
            if (apartment.buildingId != buildingId) {
                throw DomainValidationException("Apartment '$apartmentId' does not belong to building '$buildingId'")
            }
            // Catch the clash while the manager is still here to act on it.
            // Without this the link is issued happily and only fails on the
            // invitee's screen, once they have already followed it.
            if (residencyService.getCurrent(apartmentId) != null) {
                throw DomainConflictException(
                    "Unit ${apartment.unitNumber} already has a current resident; " +
                        "end that residency before inviting a new one",
                )
            }
        }

        val invitation = invitationRepository.save(
            BuildingInvitation.create(
                buildingId = buildingId,
                channel = command.channel,
                recipient = command.recipient,
                role = command.role,
                apartmentId = command.apartmentId,
                tenancy = command.tenancy,
                invitedBy = invitedBy,
            ),
        )
        notifier.notify(invitation, building.name, acceptUrlOf(invitation))
        return invitation
    }

    /**
     * Reads an invitation by its token for the join screen, before the invitee
     * has an account. Only usable invitations are returned, so an expired or
     * revoked link cannot be previewed.
     */
    @Transactional(readOnly = true)
    fun peek(token: String): BuildingInvitation {
        val invitation = invitationRepository.findByToken(token)
            ?: throw EntityNotFoundException("This invitation link is not valid")
        if (!invitation.isUsableAt(Instant.now())) {
            throw DomainConflictException("This invitation link is no longer valid")
        }
        return invitation
    }

    /**
     * Consumes the invitation for the signed-in user: marks it accepted and,
     * when it names a unit, moves them in. Both happen in one transaction, so
     * a failed move-in never burns the invitation.
     */
    fun accept(token: String, user: User): BuildingInvitation {
        val invitation = invitationRepository.findByToken(token)
            ?: throw EntityNotFoundException("This invitation link is not valid")

        if (!invitation.isAddressedTo(user.email, user.username)) {
            throw DomainConflictException("This invitation was issued for a different person")
        }
        invitation.accept(user.id, user.role)

        invitation.apartmentId?.let { apartmentId ->
            // The invitation itself is the authorization for this move-in — it
            // was only ever created by that building's own manager (see
            // create() below) — so the requester "is" that building here.
            residencyService.start(
                apartmentId,
                StartResidencyCommand(
                    residentId = user.id,
                    tenancy = invitation.tenancy ?: TenancyType.TENANT,
                ),
                requesterManagedBuildingId = invitation.buildingId,
            )
        }
        return invitationRepository.save(invitation)
    }

    fun revoke(id: InvitationId, requesterManagedBuildingId: BuildingId?): BuildingInvitation {
        val invitation = invitationRepository.findById(id)
            ?: throw InvitationNotFoundException(id)
        if (requesterManagedBuildingId != invitation.buildingId) {
            throw DomainForbiddenException("You do not manage building '${invitation.buildingId}'")
        }
        invitation.revoke()
        return invitationRepository.save(invitation)
    }

    @Transactional(readOnly = true)
    fun getAll(buildingId: BuildingId, requesterManagedBuildingId: BuildingId?): List<BuildingInvitation> {
        if (requesterManagedBuildingId != buildingId) {
            throw DomainForbiddenException("You do not manage building '$buildingId'")
        }
        return invitationRepository.findAllByBuilding(buildingId)
    }

    /** The link handed to the invitee; the token is the only secret in it. */
    fun acceptUrlOf(invitation: BuildingInvitation): String =
        "${frontendUrl.trimEnd('/')}/join?token=${invitation.token}"

    init {
        log.debug("Invitation links will point at {}", frontendUrl)
    }
}
