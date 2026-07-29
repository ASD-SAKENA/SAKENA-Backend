package com.sakena.membership.application

import com.sakena.membership.application.command.CreateInvitationCommand
import com.sakena.membership.domain.InvitationNotifier
import com.sakena.membership.domain.InvitationRepository
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.membership.domain.model.InvitationStatus
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.Building
import com.sakena.residency.application.ResidencyService
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvitationServiceTest {

    private val invitationRepository = mockk<InvitationRepository>()
    private val buildingRepository = mockk<BuildingRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val residencyService = mockk<ResidencyService>()
    private val notifier = mockk<InvitationNotifier>(relaxed = true)
    private val service = InvitationService(
        invitationRepository,
        buildingRepository,
        apartmentRepository,
        residencyService,
        notifier,
        "https://sakena.app/",
    )

    private val building = Building.create("Niloufar tower", "Tehran, Valiasr st.")
    private val invitedBy = com.sakena.user.domain.UserId.generate()

    private fun user(
        username: String = "9121234567",
        email: String = "neighbour@example.com",
        role: Role = Role.RESIDENT,
    ) = User.register(
        username = username,
        email = email,
        rawPassword = "password123",
        passwordEncoder = { it },
        role = role,
        // The aggregate ties a manager to the building they administer.
        managedBuildingId = building.id.takeIf { role == Role.MANAGER },
    )

    private fun apartment() = Apartment.create(
        buildingId = building.id,
        unitNumber = "12",
        floorNumber = 3,
        areaSquareMeters = BigDecimal("85"),
        bedrooms = 2,
    )

    private fun givenSavePassesThrough() {
        every { invitationRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `create issues the invitation and hands the link to the notifier`() {
        every { buildingRepository.findById(building.id) } returns building
        givenSavePassesThrough()
        val url = slot<String>()
        justRun { notifier.notify(any(), building.name, capture(url)) }

        val invitation = service.create(
            building.id,
            CreateInvitationCommand(
                channel = InvitationChannel.EMAIL,
                recipient = "neighbour@example.com",
                role = Role.RESIDENT,
                apartmentId = null,
                tenancy = null,
            ),
            invitedBy,
            requesterManagedBuildingId = building.id,
        )

        assertEquals("https://sakena.app/join?token=${invitation.token}", url.captured)
    }

    @Test
    fun `create validates the apartment belongs to the building`() {
        val unit = apartment()
        val otherBuilding = Building.create("Other tower", "Elsewhere")
        every { buildingRepository.findById(otherBuilding.id) } returns otherBuilding
        every { apartmentRepository.findById(unit.id) } returns unit

        assertFailsWith<DomainValidationException> {
            service.create(
                otherBuilding.id,
                CreateInvitationCommand(InvitationChannel.LINK, null, Role.RESIDENT, unit.id, null),
                invitedBy,
                requesterManagedBuildingId = otherBuilding.id,
            )
        }
    }

    @Test
    fun `create is rejected for a manager who does not administer the target building`() {
        every { buildingRepository.findById(building.id) } returns building

        assertFailsWith<DomainForbiddenException> {
            service.create(
                building.id,
                CreateInvitationCommand(InvitationChannel.LINK, null, Role.RESIDENT, null, null),
                invitedBy,
                requesterManagedBuildingId = com.sakena.property.domain.model.BuildingId.new(),
            )
        }
    }

    @Test
    fun `inviting into an unknown building is rejected`() {
        every { buildingRepository.findById(building.id) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.create(
                building.id,
                CreateInvitationCommand(InvitationChannel.LINK, null, Role.RESIDENT, null, null),
                invitedBy,
                requesterManagedBuildingId = building.id,
            )
        }
    }

    @Test
    fun `peek refuses a revoked link`() {
        val invitation = pendingInvitation()
        invitation.revoke()
        every { invitationRepository.findByToken(invitation.token) } returns invitation

        assertFailsWith<DomainConflictException> { service.peek(invitation.token) }
    }

    @Test
    fun `peek refuses an unknown link`() {
        every { invitationRepository.findByToken("nope") } returns null

        assertFailsWith<EntityNotFoundException> { service.peek("nope") }
    }

    @Test
    fun `accepting an invitation that names a unit moves the invitee in`() {
        val unit = apartment()
        val invitation = pendingInvitation(apartmentId = unit.id, tenancy = TenancyType.OWNER_OCCUPIER)
        val invitee = user()
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        givenSavePassesThrough()
        val command = slot<StartResidencyCommand>()
        every { residencyService.start(unit.id, capture(command), building.id) } returns
            Residency.start(unit.id, invitee.id, TenancyType.OWNER_OCCUPIER)

        val accepted = service.accept(invitation.token, invitee)

        assertEquals(InvitationStatus.ACCEPTED, accepted.status)
        assertEquals(invitee.id, command.captured.residentId)
        assertEquals(TenancyType.OWNER_OCCUPIER, command.captured.tenancy)
    }

    @Test
    fun `accepting an invitation without a unit leaves the invitee unassigned`() {
        val invitation = pendingInvitation()
        val invitee = user()
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        givenSavePassesThrough()

        service.accept(invitation.token, invitee)

        verify(exactly = 0) { residencyService.start(any(), any(), any()) }
    }

    @Test
    fun `someone else cannot use a targeted invitation`() {
        val invitation = pendingInvitation()
        val stranger = user(username = "9120000000", email = "stranger@example.com")
        every { invitationRepository.findByToken(invitation.token) } returns invitation

        assertFailsWith<DomainConflictException> {
            service.accept(invitation.token, stranger)
        }
        verify(exactly = 0) { invitationRepository.save(any()) }
    }

    @Test
    fun `anyone may use an open link invitation`() {
        val invitation = pendingInvitation(channel = InvitationChannel.LINK, recipient = null)
        val stranger = user(username = "9120000000", email = "stranger@example.com")
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        givenSavePassesThrough()

        val accepted = service.accept(invitation.token, stranger)

        assertTrue(accepted.status == InvitationStatus.ACCEPTED)
    }

    @Test
    fun `a manager cannot accept a resident invitation`() {
        val unit = apartment()
        val invitation = pendingInvitation(
            channel = InvitationChannel.LINK,
            recipient = null,
            apartmentId = unit.id,
        )
        val manager = user(role = Role.MANAGER)
        every { invitationRepository.findByToken(invitation.token) } returns invitation

        assertFailsWith<DomainConflictException> {
            service.accept(invitation.token, manager)
        }
        // Neither the unit nor the invitation may be consumed by the failed join.
        verify(exactly = 0) { residencyService.start(any(), any(), any()) }
        verify(exactly = 0) { invitationRepository.save(any()) }
    }

    @Test
    fun `staff cannot accept an invitation that assigns a unit`() {
        val unit = apartment()
        val invitation = pendingInvitation(
            channel = InvitationChannel.LINK,
            recipient = null,
            apartmentId = unit.id,
        )
        val staff = user(role = Role.STAFF)
        every { invitationRepository.findByToken(invitation.token) } returns invitation

        assertFailsWith<DomainConflictException> {
            service.accept(invitation.token, staff)
        }
        verify(exactly = 0) { residencyService.start(any(), any(), any()) }
        verify(exactly = 0) { invitationRepository.save(any()) }
    }

    @Test
    fun `staff may accept an invitation that assigns no unit`() {
        val invitation = pendingInvitation(channel = InvitationChannel.LINK, recipient = null)
        val staff = user(role = Role.STAFF)
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        givenSavePassesThrough()

        val accepted = service.accept(invitation.token, staff)

        assertEquals(InvitationStatus.ACCEPTED, accepted.status)
        verify(exactly = 0) { residencyService.start(any(), any(), any()) }
    }

    @Test
    fun `revoke is rejected for a manager who does not administer the invitation's building`() {
        val invitation = pendingInvitation()
        every { invitationRepository.findById(invitation.id) } returns invitation

        assertFailsWith<DomainForbiddenException> {
            service.revoke(invitation.id, requesterManagedBuildingId = com.sakena.property.domain.model.BuildingId.new())
        }
    }

    @Test
    fun `getAll is rejected for a building the requester does not administer`() {
        assertFailsWith<DomainForbiddenException> {
            service.getAll(building.id, requesterManagedBuildingId = com.sakena.property.domain.model.BuildingId.new())
        }
    }

    private fun pendingInvitation(
        channel: InvitationChannel = InvitationChannel.EMAIL,
        recipient: String? = "neighbour@example.com",
        apartmentId: com.sakena.property.domain.model.ApartmentId? = null,
        tenancy: TenancyType? = null,
    ) = BuildingInvitation.create(
        buildingId = building.id,
        channel = channel,
        recipient = recipient,
        role = Role.RESIDENT,
        apartmentId = apartmentId,
        tenancy = tenancy,
        invitedBy = invitedBy,
    )
}
