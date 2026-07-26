package com.sakena.membership.domain.model

import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/** Value object identifying a [BuildingInvitation] aggregate. */
@JvmInline
value class InvitationId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): InvitationId = InvitationId(UUID.randomUUID())

        fun from(raw: String): InvitationId =
            try {
                InvitationId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid invitation id")
            }
    }
}

/** How the invitation reached its recipient. */
enum class InvitationChannel {
    /** Addressed to an email; the link is emailed to it. */
    EMAIL,

    /** Addressed to a mobile number; the manager shares the link. */
    PHONE,

    /** Open link with no fixed recipient — anyone holding it may join. */
    LINK,
}

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED,
}

/**
 * BuildingInvitation aggregate root — an invitation to join a building, either
 * addressed to a specific email/phone or as an open link.
 *
 * The secret token is what the recipient presents; it is generated here and
 * never derived from the id, so knowing an invitation exists is not enough to
 * accept it.
 */
class BuildingInvitation private constructor(
    val id: InvitationId,
    val buildingId: BuildingId,
    val token: String,
    val channel: InvitationChannel,
    /** Normalised email or phone; null for an open link. */
    val recipient: String?,
    val role: Role,
    /** Unit the invitee moves into on acceptance; null leaves them unassigned. */
    val apartmentId: ApartmentId?,
    val tenancy: TenancyType?,
    val invitedBy: UserId,
    val createdAt: Instant,
    val expiresAt: Instant,
    status: InvitationStatus,
    acceptedBy: UserId?,
    acceptedAt: Instant?,
) {
    var status: InvitationStatus = status
        private set

    var acceptedBy: UserId? = acceptedBy
        private set

    var acceptedAt: Instant? = acceptedAt
        private set

    /** Expiry is derived, so a stale PENDING row never counts as usable. */
    fun isUsableAt(now: Instant): Boolean =
        status == InvitationStatus.PENDING && now.isBefore(expiresAt)

    fun accept(userId: UserId, now: Instant = Instant.now()) {
        if (status != InvitationStatus.PENDING) {
            throw DomainConflictException("This invitation is no longer pending")
        }
        if (!now.isBefore(expiresAt)) {
            status = InvitationStatus.EXPIRED
            throw DomainConflictException("This invitation has expired")
        }
        status = InvitationStatus.ACCEPTED
        acceptedBy = userId
        acceptedAt = now
    }

    fun revoke() {
        if (status == InvitationStatus.ACCEPTED) {
            throw DomainConflictException("An accepted invitation cannot be revoked")
        }
        status = InvitationStatus.REVOKED
    }

    /**
     * Whether the given identity may use this invitation. An open link accepts
     * anyone; a targeted one only its addressee.
     */
    fun isAddressedTo(email: String?, phone: String?): Boolean =
        when (channel) {
            InvitationChannel.LINK -> true
            InvitationChannel.EMAIL -> recipient != null && recipient.equals(email?.trim(), true)
            InvitationChannel.PHONE -> recipient == normalizePhone(phone)
        }

    companion object {
        const val DEFAULT_VALIDITY_DAYS = 14L
        private const val TOKEN_BYTES = 32

        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val random = SecureRandom()

        fun create(
            buildingId: BuildingId,
            channel: InvitationChannel,
            recipient: String?,
            role: Role,
            apartmentId: ApartmentId?,
            tenancy: TenancyType?,
            invitedBy: UserId,
            validityDays: Long = DEFAULT_VALIDITY_DAYS,
        ): BuildingInvitation {
            val normalized = validateRecipient(channel, recipient)
            if (role == Role.MANAGER) {
                throw DomainValidationException("Managers are not onboarded through an invitation")
            }
            if (apartmentId != null && role == Role.STAFF) {
                throw DomainValidationException("Service staff are not assigned to a unit")
            }
            val now = Instant.now()
            return BuildingInvitation(
                id = InvitationId.new(),
                buildingId = buildingId,
                token = generateToken(),
                channel = channel,
                recipient = normalized,
                role = role,
                apartmentId = apartmentId,
                tenancy = if (apartmentId == null) null else (tenancy ?: TenancyType.TENANT),
                invitedBy = invitedBy,
                createdAt = now,
                expiresAt = now.plus(validityDays, ChronoUnit.DAYS),
                status = InvitationStatus.PENDING,
                acceptedBy = null,
                acceptedAt = null,
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        @Suppress("LongParameterList")
        fun reconstitute(
            id: InvitationId,
            buildingId: BuildingId,
            token: String,
            channel: InvitationChannel,
            recipient: String?,
            role: Role,
            apartmentId: ApartmentId?,
            tenancy: TenancyType?,
            invitedBy: UserId,
            createdAt: Instant,
            expiresAt: Instant,
            status: InvitationStatus,
            acceptedBy: UserId?,
            acceptedAt: Instant?,
        ): BuildingInvitation = BuildingInvitation(
            id, buildingId, token, channel, recipient, role, apartmentId, tenancy,
            invitedBy, createdAt, expiresAt, status, acceptedBy, acceptedAt,
        )

        /** Digits only, so "۰۹۱۲…", "+98912…" and "0912…" compare equal. */
        fun normalizePhone(phone: String?): String? {
            val digits = phone?.trim()
                ?.map { char -> "۰۱۲۳۴۵۶۷۸۹".indexOf(char).takeIf { it >= 0 }?.digitToChar() ?: char }
                ?.joinToString("")
                ?.filter { it.isDigit() }
                ?: return null
            if (digits.isEmpty()) return null
            // Reduce +98/0098/0 prefixes to the bare national number.
            val national = digits.removePrefix("0098").removePrefix("98").removePrefix("0")
            return national.takeIf { it.isNotEmpty() }
        }

        private fun validateRecipient(channel: InvitationChannel, recipient: String?): String? =
            when (channel) {
                InvitationChannel.LINK -> null
                InvitationChannel.EMAIL -> {
                    val trimmed = recipient?.trim()?.lowercase()
                    if (trimmed.isNullOrEmpty() || !EMAIL_PATTERN.matches(trimmed)) {
                        throw DomainValidationException("A valid email is required for an email invitation")
                    }
                    trimmed
                }
                InvitationChannel.PHONE -> normalizePhone(recipient)
                    ?: throw DomainValidationException("A valid phone number is required for a phone invitation")
            }

        private fun generateToken(): String {
            val bytes = ByteArray(TOKEN_BYTES)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
