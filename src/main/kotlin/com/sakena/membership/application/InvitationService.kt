package com.sakena.membership.application

import com.sakena.membership.application.command.CreateInvitationCommand
import com.sakena.membership.domain.InvitationNotFoundException
import com.sakena.membership.domain.InvitationNotifier
import com.sakena.membership.domain.InvitationRepository
import com.sakena.membership.domain.StaffBuildingMembershipRepository
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationId
import com.sakena.membership.domain.model.StaffBuildingMembership
import com.sakena.property.domain.ApartmentNotFoundException
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.ResidencyService
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
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
    private val buildingAccess: BuildingAccess,
    private val staffMembershipRepository: StaffBuildingMembershipRepository,
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
    ): BuildingInvitation {
        val building = buildingRepository.findById(buildingId)
            ?: throw EntityNotFoundException("Building with id '$buildingId' was not found")
        buildingAccess.requireManagerAccess(buildingId, invitedBy)
        command.apartmentId?.let { apartmentId ->
            val apartment = apartmentRepository.findById(apartmentId)
                ?: throw ApartmentNotFoundException(apartmentId)
            if (apartment.buildingId != buildingId) {
                throw DomainConflictException("The invited apartment does not belong to the invited building")
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
        requireApartmentBelongsToInvitationBuilding(invitation)
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
        val newStaffMembership = newStaffMembership(invitation, user)
        invitation.accept(user.id)

        invitation.apartmentId?.let { apartmentId ->
            residencyService.startFromInvitation(
                apartmentId,
                invitation.buildingId,
                StartResidencyCommand(
                    residentId = user.id,
                    tenancy = invitation.tenancy ?: TenancyType.TENANT,
                ),
            )
        }
        newStaffMembership?.let(staffMembershipRepository::save)
        return invitationRepository.save(invitation)
    }

    fun revoke(id: InvitationId, managerId: UserId): BuildingInvitation {
        val invitation = invitationRepository.findById(id)
            ?: throw InvitationNotFoundException(id)
        buildingAccess.requireManagerAccess(invitation.buildingId, managerId)
        invitation.revoke()
        return invitationRepository.save(invitation)
    }

    @Transactional(readOnly = true)
    fun getAll(buildingId: BuildingId, managerId: UserId): List<BuildingInvitation> {
        buildingAccess.requireManagerAccess(buildingId, managerId)
        return invitationRepository.findAllByBuilding(buildingId)
    }

    /** The link handed to the invitee; the token is the only secret in it. */
    fun acceptUrlOf(invitation: BuildingInvitation): String =
        "${frontendUrl.trimEnd('/')}/join?token=${invitation.token}"

    private fun requireApartmentBelongsToInvitationBuilding(invitation: BuildingInvitation) {
        val apartmentId = invitation.apartmentId ?: return
        val apartment = apartmentRepository.findById(apartmentId)
            ?: throw DomainConflictException("The apartment assigned to this invitation is no longer available")
        if (apartment.buildingId != invitation.buildingId) {
            throw DomainConflictException("The invited apartment does not belong to the invited building")
        }
    }

    private fun newStaffMembership(
        invitation: BuildingInvitation,
        user: User,
    ): StaffBuildingMembership? {
        if (invitation.role != Role.STAFF) return null
        if (user.role != Role.STAFF) {
            throw DomainConflictException("This invitation is only valid for a service-staff account")
        }

        val existing = staffMembershipRepository.findByStaffId(user.id)
        if (existing == null) {
            return StaffBuildingMembership.create(user.id, invitation.buildingId)
        }
        if (existing.buildingId != invitation.buildingId) {
            throw DomainConflictException("This staff member is already assigned to another building")
        }
        return null
    }

    init {
        log.debug("Invitation links will point at {}", frontendUrl)
    }
}
