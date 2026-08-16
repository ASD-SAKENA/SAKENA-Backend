package com.sakena.membership.application

import com.sakena.membership.application.command.CreateInvitationCommand
import com.sakena.membership.domain.InvitationNotifier
import com.sakena.membership.domain.InvitationRepository
import com.sakena.membership.domain.StaffBuildingMembershipRepository
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationChannel
import com.sakena.membership.domain.model.InvitationStatus
import com.sakena.membership.domain.model.StaffBuildingMembership
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.Building
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.ResidencyService
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
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
    private val buildingAccess = mockk<BuildingAccess>()
    private val staffMembershipRepository = mockk<StaffBuildingMembershipRepository>()
    private val residencyService = mockk<ResidencyService>()
    private val notifier = mockk<InvitationNotifier>(relaxed = true)
    private val service = InvitationService(
        invitationRepository,
        buildingRepository,
        apartmentRepository,
        buildingAccess,
        staffMembershipRepository,
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
        justRun { buildingAccess.requireManagerAccess(building.id, invitedBy) }
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
        )

        assertEquals("https://sakena.app/join?token=${invitation.token}", url.captured)
    }

    @Test
    fun `inviting into an unknown building is rejected`() {
        every { buildingRepository.findById(building.id) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.create(
                building.id,
                CreateInvitationCommand(InvitationChannel.LINK, null, Role.RESIDENT, null, null),
                invitedBy,
            )
        }
    }

    @Test
    fun `manager cannot create an invitation for another building`() {
        every { buildingRepository.findById(building.id) } returns building
        every {
            buildingAccess.requireManagerAccess(building.id, invitedBy)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.create(
                building.id,
                CreateInvitationCommand(InvitationChannel.LINK, null, Role.RESIDENT, null, null),
                invitedBy,
            )
        }
        verify(exactly = 0) { invitationRepository.save(any()) }
    }

    @Test
    fun `invitation apartment must belong to its building`() {
        val otherApartment = Apartment.create(
            buildingId = BuildingId.new(),
            unitNumber = "99",
            floorNumber = 9,
            areaSquareMeters = BigDecimal.TEN,
            bedrooms = 1,
        )
        every { buildingRepository.findById(building.id) } returns building
        justRun { buildingAccess.requireManagerAccess(building.id, invitedBy) }
        every { apartmentRepository.findById(otherApartment.id) } returns otherApartment

        assertFailsWith<DomainConflictException> {
            service.create(
                building.id,
                CreateInvitationCommand(
                    InvitationChannel.LINK,
                    null,
                    Role.RESIDENT,
                    otherApartment.id,
                    TenancyType.TENANT,
                ),
                invitedBy,
            )
        }
        verify(exactly = 0) { invitationRepository.save(any()) }
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
    fun `peek rejects a legacy invitation whose apartment belongs to another building`() {
        val otherApartment = Apartment.create(
            buildingId = BuildingId.new(),
            unitNumber = "99",
            floorNumber = 9,
            areaSquareMeters = BigDecimal.TEN,
            bedrooms = 1,
        )
        val invitation = pendingInvitation(apartmentId = otherApartment.id)
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        every { apartmentRepository.findById(otherApartment.id) } returns otherApartment

        assertFailsWith<DomainConflictException> { service.peek(invitation.token) }
    }

    @Test
    fun `accepting an invitation that names a unit moves the invitee in`() {
        val unit = apartment()
        val invitation = pendingInvitation(apartmentId = unit.id, tenancy = TenancyType.OWNER_OCCUPIER)
        val invitee = user()
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        givenSavePassesThrough()
        val command = slot<StartResidencyCommand>()
        every { residencyService.startFromInvitation(unit.id, building.id, capture(command)) } returns
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

        verify(exactly = 0) { residencyService.startFromInvitation(any(), any(), any()) }
    }

    @Test
    fun `accepting a staff invitation assigns the staff member to its building`() {
        val invitation = pendingInvitation(
            channel = InvitationChannel.LINK,
            recipient = null,
            role = Role.STAFF,
        )
        val staff = user(
            username = "staff-user",
            email = "staff@sakena.test",
            role = Role.STAFF,
        )
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        every { staffMembershipRepository.findByStaffId(staff.id) } returns null
        val savedMembership = slot<StaffBuildingMembership>()
        every { staffMembershipRepository.save(capture(savedMembership)) } answers { savedMembership.captured }
        givenSavePassesThrough()

        val accepted = service.accept(invitation.token, staff)

        assertEquals(InvitationStatus.ACCEPTED, accepted.status)
        assertEquals(staff.id, savedMembership.captured.staffId)
        assertEquals(building.id, savedMembership.captured.buildingId)
        verify(exactly = 0) { residencyService.startFromInvitation(any(), any(), any()) }
    }

    @Test
    fun `staff cannot accept an invitation for a second building`() {
        val invitation = pendingInvitation(
            channel = InvitationChannel.LINK,
            recipient = null,
            role = Role.STAFF,
        )
        val staff = user(
            username = "staff-user",
            email = "staff@sakena.test",
            role = Role.STAFF,
        )
        every { invitationRepository.findByToken(invitation.token) } returns invitation
        every { staffMembershipRepository.findByStaffId(staff.id) } returns
            StaffBuildingMembership.create(staff.id, BuildingId.new())

        assertFailsWith<DomainConflictException> { service.accept(invitation.token, staff) }

        assertEquals(InvitationStatus.PENDING, invitation.status)
        verify(exactly = 0) { staffMembershipRepository.save(any()) }
        verify(exactly = 0) { invitationRepository.save(any()) }
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
    fun `manager cannot list invitations from another building`() {
        every {
            buildingAccess.requireManagerAccess(building.id, invitedBy)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.getAll(building.id, invitedBy) }
        verify(exactly = 0) { invitationRepository.findAllByBuilding(any()) }
    }

    @Test
    fun `manager cannot revoke an invitation from another building`() {
        val invitation = pendingInvitation()
        every { invitationRepository.findById(invitation.id) } returns invitation
        every {
            buildingAccess.requireManagerAccess(building.id, invitedBy)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.revoke(invitation.id, invitedBy) }
        assertEquals(InvitationStatus.PENDING, invitation.status)
        verify(exactly = 0) { invitationRepository.save(any()) }
    }

    private fun pendingInvitation(
        channel: InvitationChannel = InvitationChannel.EMAIL,
        recipient: String? = "neighbour@example.com",
        apartmentId: com.sakena.property.domain.model.ApartmentId? = null,
        tenancy: TenancyType? = null,
        role: Role = Role.RESIDENT,
    ) = BuildingInvitation.create(
        buildingId = building.id,
        channel = channel,
        recipient = recipient,
        role = role,
        apartmentId = apartmentId,
        tenancy = tenancy,
        invitedBy = invitedBy,
    )
}
