package com.sakena.residency.application

import com.sakena.notification.application.NotificationService
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ResidencyServiceTest {

    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val buildingRepository = mockk<BuildingRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val service = ResidencyService(
        residencyRepository,
        apartmentRepository,
        buildingRepository,
        userRepository,
        notificationService,
    )

    private val buildingId = BuildingId.new()
    private val apartment = Apartment.create(buildingId, "12A", 1, BigDecimal("40"), 1)
    private val apartmentId = apartment.id

    init {
        every { buildingRepository.existsById(buildingId) } returns true
    }

    private fun user(role: Role = Role.RESIDENT) = User.register(
        username = "resident-${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        rawPassword = "password123",
        passwordEncoder = { it },
        role = role,
    )

    private fun givenVacantUnitAnd(resident: User) {
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null
        every { residencyRepository.findActiveByResident(resident.id) } returns null
    }

    @Test
    fun `start links a resident to a vacant unit`() {
        val resident = user()
        givenVacantUnitAnd(resident)
        val saved = slot<Residency>()
        every { residencyRepository.save(capture(saved)) } answers { saved.captured }

        val residency = service.start(
            apartmentId,
            StartResidencyCommand(resident.id, TenancyType.OWNER_OCCUPIER),
            requesterManagedBuildingId = buildingId,
        )

        assertEquals(apartmentId, residency.apartmentId)
        assertEquals(resident.id, residency.residentId)
        assertEquals(TenancyType.OWNER_OCCUPIER, residency.tenancy)
    }

    @Test
    fun `start is rejected for a manager who does not administer the unit's building`() {
        val resident = user()
        givenVacantUnitAnd(resident)

        assertFailsWith<DomainForbiddenException> {
            service.start(
                apartmentId,
                StartResidencyCommand(resident.id, TenancyType.TENANT),
                requesterManagedBuildingId = BuildingId.new(),
            )
        }
        verify(exactly = 0) { residencyRepository.save(any()) }
    }

    @Test
    fun `a unit cannot take a second current resident`() {
        val resident = user()
        val occupier = user()
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns
            Residency.start(apartmentId, occupier.id, TenancyType.TENANT)

        assertFailsWith<DomainConflictException> {
            service.start(
                apartmentId,
                StartResidencyCommand(resident.id, TenancyType.TENANT),
                requesterManagedBuildingId = buildingId,
            )
        }
        verify(exactly = 0) { residencyRepository.save(any()) }
    }

    @Test
    fun `a resident cannot occupy two units at once`() {
        val resident = user()
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null
        every { residencyRepository.findActiveByResident(resident.id) } returns
            Residency.start(ApartmentId.new(), resident.id, TenancyType.TENANT)

        assertFailsWith<DomainConflictException> {
            service.start(
                apartmentId,
                StartResidencyCommand(resident.id, TenancyType.TENANT),
                requesterManagedBuildingId = buildingId,
            )
        }
    }

    @Test
    fun `service staff cannot be registered as a unit resident`() {
        val worker = user(Role.STAFF)
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { userRepository.findById(worker.id) } returns worker

        assertFailsWith<DomainConflictException> {
            service.start(
                apartmentId,
                StartResidencyCommand(worker.id, TenancyType.TENANT),
                requesterManagedBuildingId = buildingId,
            )
        }
    }

    @Test
    fun `starting a residency in an unknown unit is rejected`() {
        val resident = user()
        every { apartmentRepository.findById(apartmentId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.start(
                apartmentId,
                StartResidencyCommand(resident.id, TenancyType.TENANT),
                requesterManagedBuildingId = buildingId,
            )
        }
    }

    @Test
    fun `ending the current residency leaves the unit vacant`() {
        val resident = user()
        val residency = Residency.start(apartmentId, resident.id, TenancyType.TENANT)
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { residencyRepository.findActiveByApartment(apartmentId) } returns residency
        every { residencyRepository.save(any()) } answers { firstArg() }

        val ended = service.endCurrent(apartmentId, requesterManagedBuildingId = buildingId)

        assertFalse(ended.active)
    }

    @Test
    fun `ending a residency is rejected for a manager who does not administer the unit's building`() {
        val resident = user()
        val residency = Residency.start(apartmentId, resident.id, TenancyType.TENANT)
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { residencyRepository.findActiveByApartment(apartmentId) } returns residency

        assertFailsWith<DomainForbiddenException> {
            service.endCurrent(apartmentId, requesterManagedBuildingId = BuildingId.new())
        }
    }

    @Test
    fun `ending a residency of a vacant unit is rejected`() {
        every { apartmentRepository.findById(apartmentId) } returns apartment
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.endCurrent(apartmentId, requesterManagedBuildingId = buildingId)
        }
    }

    @Test
    fun `getActiveByBuilding with a building id scopes the query to that building`() {
        val residency = Residency.start(apartmentId, user().id, TenancyType.TENANT)
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency)

        val residencies = service.getActiveByBuilding(buildingId, requesterManagedBuildingId = buildingId)

        assertEquals(listOf(residency), residencies)
    }

    @Test
    fun `getActiveByBuilding with no building id defaults to the requester's own building`() {
        val residency = Residency.start(apartmentId, user().id, TenancyType.TENANT)
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency)

        val residencies = service.getActiveByBuilding(null, requesterManagedBuildingId = buildingId)

        assertEquals(listOf(residency), residencies)
    }

    @Test
    fun `getActiveByBuilding with no managed building returns every active residency`() {
        val residency = Residency.start(apartmentId, user().id, TenancyType.TENANT)
        every { residencyRepository.findAllActive() } returns listOf(residency)

        val residencies = service.getActiveByBuilding(null, requesterManagedBuildingId = null)

        assertEquals(listOf(residency), residencies)
    }

    @Test
    fun `getActiveByBuilding is rejected for a building the requester does not administer`() {
        assertFailsWith<DomainForbiddenException> {
            service.getActiveByBuilding(BuildingId.new(), requesterManagedBuildingId = buildingId)
        }
    }

    @Test
    fun `requireActiveResidency returns the active residency`() {
        val resident = user()
        val residency = Residency.start(apartmentId, resident.id, TenancyType.TENANT)
        every { residencyRepository.findActiveByResident(resident.id) } returns residency

        assertEquals(residency, service.requireActiveResidency(resident.id))
    }

    @Test
    fun `requireActiveResidency rejects a resident with no active unit`() {
        val resident = user()
        every { residencyRepository.findActiveByResident(resident.id) } returns null

        assertFailsWith<DomainForbiddenException> {
            service.requireActiveResidency(resident.id)
        }
    }
}
