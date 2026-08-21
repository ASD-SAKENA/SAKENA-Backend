package com.sakena.support.infrastructure.persistence

import com.sakena.property.domain.model.BuildingId
import com.sakena.support.domain.SupportTicketRepository
import com.sakena.support.domain.TicketMessageRepository
import com.sakena.support.domain.model.SupportTicket
import com.sakena.support.domain.model.TicketAttachment
import com.sakena.support.domain.model.TicketId
import com.sakena.support.domain.model.TicketMessage
import com.sakena.support.domain.model.TicketMessageId
import com.sakena.support.domain.model.TicketMessageKind
import com.sakena.support.domain.model.TicketStatus
import com.sakena.user.domain.UserId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

interface SupportTicketJpaRepository : JpaRepository<SupportTicketEntity, UUID> {
    fun findAllByRaisedByOrderByLastMessageAtDesc(raisedBy: UUID): List<SupportTicketEntity>

    fun findAllByBuildingIdOrderByLastMessageAtDesc(buildingId: UUID): List<SupportTicketEntity>

    fun findAllByBuildingIdAndStatusOrderByLastMessageAtDesc(
        buildingId: UUID,
        status: TicketStatus,
    ): List<SupportTicketEntity>
}

interface TicketMessageJpaRepository : JpaRepository<TicketMessageEntity, UUID> {
    fun findAllByTicketIdOrderBySentAtAsc(ticketId: UUID): List<TicketMessageEntity>
}

@Component
class SupportTicketRepositoryAdapter(
    private val jpaRepository: SupportTicketJpaRepository,
) : SupportTicketRepository {

    override fun save(ticket: SupportTicket): SupportTicket {
        jpaRepository.save(
            SupportTicketEntity(
                id = ticket.id.value,
                buildingId = ticket.buildingId.value,
                raisedBy = ticket.raisedBy.value,
                category = ticket.category,
                subject = ticket.subject,
                anonymous = ticket.anonymous,
                status = ticket.status,
                createdAt = ticket.createdAt,
                updatedAt = ticket.updatedAt,
                lastMessageAt = ticket.lastMessageAt,
            ),
        )
        return ticket
    }

    override fun findById(id: TicketId): SupportTicket? =
        jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findAllByResident(residentId: UserId): List<SupportTicket> =
        jpaRepository.findAllByRaisedByOrderByLastMessageAtDesc(residentId.value).map(::toDomain)

    override fun findAllByBuilding(buildingId: BuildingId, status: TicketStatus?): List<SupportTicket> =
        if (status == null) {
            jpaRepository.findAllByBuildingIdOrderByLastMessageAtDesc(buildingId.value)
        } else {
            jpaRepository.findAllByBuildingIdAndStatusOrderByLastMessageAtDesc(buildingId.value, status)
        }.map(::toDomain)

    private fun toDomain(entity: SupportTicketEntity): SupportTicket =
        SupportTicket.reconstitute(
            id = TicketId(entity.id),
            buildingId = BuildingId(entity.buildingId),
            raisedBy = UserId(entity.raisedBy),
            category = entity.category,
            subject = entity.subject,
            anonymous = entity.anonymous,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastMessageAt = entity.lastMessageAt,
        )
}

@Component
class TicketMessageRepositoryAdapter(
    private val jpaRepository: TicketMessageJpaRepository,
) : TicketMessageRepository {

    override fun save(message: TicketMessage): TicketMessage {
        jpaRepository.save(
            TicketMessageEntity(
                id = message.id.value,
                ticketId = message.ticketId.value,
                authorId = message.authorId.value,
                kind = message.kind,
                body = message.body,
                attachmentKey = message.attachment?.storageKey,
                attachmentContentType = message.attachment?.contentType,
                attachmentSizeBytes = message.attachment?.sizeBytes,
                attachmentDurationSeconds = message.attachment?.durationSeconds,
                sentAt = message.sentAt,
            ),
        )
        return message
    }

    override fun findAllByTicket(ticketId: TicketId): List<TicketMessage> =
        jpaRepository.findAllByTicketIdOrderBySentAtAsc(ticketId.value).map(::toDomain)

    private fun toDomain(entity: TicketMessageEntity): TicketMessage =
        TicketMessage.reconstitute(
            id = TicketMessageId(entity.id),
            ticketId = TicketId(entity.ticketId),
            authorId = UserId(entity.authorId),
            kind = entity.kind,
            body = entity.body,
            attachment = entity.attachmentKey?.let { key ->
                TicketAttachment(
                    storageKey = key,
                    contentType = entity.attachmentContentType.orEmpty(),
                    sizeBytes = entity.attachmentSizeBytes ?: 0,
                    durationSeconds = entity.attachmentDurationSeconds,
                )
            },
            sentAt = entity.sentAt,
        )
}
