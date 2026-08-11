package com.sakena.membership.infrastructure.persistence

import com.sakena.membership.domain.InvitationRepository
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationId
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

interface InvitationJpaRepository : JpaRepository<InvitationEntity, UUID> {
    fun findByToken(token: String): InvitationEntity?

    fun findAllByBuildingIdOrderByCreatedAtDesc(buildingId: UUID): List<InvitationEntity>
}

/**
 * Adapter implementing the domain [InvitationRepository] port on top of Spring
 * Data JPA.
 */
@Component
class InvitationRepositoryAdapter(
    private val jpaRepository: InvitationJpaRepository,
) : InvitationRepository {

    override fun save(invitation: BuildingInvitation): BuildingInvitation {
        jpaRepository.save(toEntity(invitation))
        return invitation
    }

    override fun findById(id: InvitationId): BuildingInvitation? =
        jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findByToken(token: String): BuildingInvitation? =
        jpaRepository.findByToken(token)?.let(::toDomain)

    override fun findAllByBuilding(buildingId: BuildingId): List<BuildingInvitation> =
        jpaRepository.findAllByBuildingIdOrderByCreatedAtDesc(buildingId.value).map(::toDomain)

    private fun toEntity(invitation: BuildingInvitation): InvitationEntity =
        InvitationEntity(
            id = invitation.id.value,
            buildingId = invitation.buildingId.value,
            token = invitation.token,
            channel = invitation.channel,
            recipient = invitation.recipient,
            role = invitation.role,
            apartmentId = invitation.apartmentId?.value,
            tenancy = invitation.tenancy,
            invitedBy = invitation.invitedBy.value,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
            status = invitation.status,
            acceptedBy = invitation.acceptedBy?.value,
            acceptedAt = invitation.acceptedAt,
        )

    private fun toDomain(entity: InvitationEntity): BuildingInvitation =
        BuildingInvitation.reconstitute(
            id = InvitationId(entity.id),
            buildingId = BuildingId(entity.buildingId),
            token = entity.token,
            channel = entity.channel,
            recipient = entity.recipient,
            role = entity.role,
            apartmentId = entity.apartmentId?.let(::ApartmentId),
            tenancy = entity.tenancy,
            invitedBy = UserId(entity.invitedBy),
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt,
            status = entity.status,
            acceptedBy = entity.acceptedBy?.let(::UserId),
            acceptedAt = entity.acceptedAt,
        )
}
