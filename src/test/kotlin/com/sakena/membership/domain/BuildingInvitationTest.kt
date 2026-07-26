package com.sakena.membership.domain

import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.membership.domain.model.InvitationStatus
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildingInvitationTest {

    private val buildingId = BuildingId.new()
    private val manager = UserId.generate()

    private fun invite(
        channel: InvitationChannel = InvitationChannel.EMAIL,
        recipient: String? = "Neighbour@Example.com",
        role: Role = Role.RESIDENT,
        apartmentId: ApartmentId? = null,
        tenancy: TenancyType? = null,
    ) = BuildingInvitation.create(
        buildingId = buildingId,
        channel = channel,
        recipient = recipient,
        role = role,
        apartmentId = apartmentId,
        tenancy = tenancy,
        invitedBy = manager,
    )

    @Test
    fun `an email invitation normalises its recipient and starts pending`() {
        val invitation = invite()

        assertEquals("neighbour@example.com", invitation.recipient)
        assertEquals(InvitationStatus.PENDING, invitation.status)
        assertTrue(invitation.isUsableAt(Instant.now()))
    }

    @Test
    fun `each invitation gets its own unguessable token`() {
        assertNotEquals(invite().token, invite().token)
    }

    @Test
    fun `an invalid email is rejected`() {
        assertFailsWith<DomainValidationException> { invite(recipient = "not-an-email") }
    }

    @Test
    fun `phone recipients are normalised to the bare national number`() {
        val fromLocal = invite(channel = InvitationChannel.PHONE, recipient = "09121234567")
        val fromIntl = invite(channel = InvitationChannel.PHONE, recipient = "+98 912 123 4567")
        val fromPersianDigits =
            invite(channel = InvitationChannel.PHONE, recipient = "۰۹۱۲۱۲۳۴۵۶۷")

        assertEquals("9121234567", fromLocal.recipient)
        assertEquals(fromLocal.recipient, fromIntl.recipient)
        assertEquals(fromLocal.recipient, fromPersianDigits.recipient)
    }

    @Test
    fun `an open link has no recipient and accepts anyone`() {
        val invitation = invite(channel = InvitationChannel.LINK, recipient = "ignored@example.com")

        assertNull(invitation.recipient)
        assertTrue(invitation.isAddressedTo("someone@else.com", "9120000000"))
    }

    @Test
    fun `a targeted invitation only accepts its addressee`() {
        val invitation = invite()

        assertTrue(invitation.isAddressedTo("NEIGHBOUR@example.com", null))
        assertFalse(invitation.isAddressedTo("stranger@example.com", null))
    }

    @Test
    fun `a phone invitation matches regardless of how the number is written`() {
        val invitation = invite(channel = InvitationChannel.PHONE, recipient = "09121234567")

        assertTrue(invitation.isAddressedTo(null, "+989121234567"))
        assertFalse(invitation.isAddressedTo(null, "09120000000"))
    }

    @Test
    fun `managers are not onboarded through invitations`() {
        assertFailsWith<DomainValidationException> { invite(role = Role.MANAGER) }
    }

    @Test
    fun `service staff cannot be invited into a unit`() {
        assertFailsWith<DomainValidationException> {
            invite(role = Role.STAFF, apartmentId = ApartmentId.new())
        }
    }

    @Test
    fun `an invitation naming a unit defaults its tenancy`() {
        val invitation = invite(apartmentId = ApartmentId.new())

        assertEquals(TenancyType.TENANT, invitation.tenancy)
    }

    @Test
    fun `accepting marks the invitation used and records who used it`() {
        val invitation = invite()
        val invitee = UserId.generate()

        invitation.accept(invitee)

        assertEquals(InvitationStatus.ACCEPTED, invitation.status)
        assertEquals(invitee, invitation.acceptedBy)
        assertFalse(invitation.isUsableAt(Instant.now()))
    }

    @Test
    fun `an invitation cannot be accepted twice`() {
        val invitation = invite()
        invitation.accept(UserId.generate())

        assertFailsWith<DomainConflictException> { invitation.accept(UserId.generate()) }
    }

    @Test
    fun `an expired invitation is refused and marked expired`() {
        val invitation = invite()
        val afterExpiry = invitation.expiresAt.plus(1, ChronoUnit.DAYS)

        assertFalse(invitation.isUsableAt(afterExpiry))
        assertFailsWith<DomainConflictException> {
            invitation.accept(UserId.generate(), now = afterExpiry)
        }
        assertEquals(InvitationStatus.EXPIRED, invitation.status)
    }

    @Test
    fun `a revoked invitation is no longer usable`() {
        val invitation = invite()

        invitation.revoke()

        assertEquals(InvitationStatus.REVOKED, invitation.status)
        assertFalse(invitation.isUsableAt(Instant.now()))
    }

    @Test
    fun `an accepted invitation cannot be revoked`() {
        val invitation = invite()
        invitation.accept(UserId.generate())

        assertFailsWith<DomainConflictException> { invitation.revoke() }
    }
}
