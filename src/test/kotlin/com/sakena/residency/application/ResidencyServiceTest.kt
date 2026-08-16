package com.sakena.residency.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
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
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ResidencyServiceTest {

    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val buildingRepository = mockk<BuildingRepository>(relaxed = true)
    private val buildingAccess = mockk<BuildingAccess>()
    private val userRepository = mockk<UserRepository>()
    private val service = ResidencyService(
        residencyRepository,
        apartmentRepository,
        buildingRepository,
        buildingAccess,
        userRepository,
    )

    private val apartmentId = ApartmentId.new()
    private val buildingId = BuildingId.new()
    private val managerId = com.sakena.user.domain.UserId.generate()

    private fun user(role: Role = Role.RESIDENT) = User.register(
        username = "resident-${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        rawPassword = "password123",
        passwordEncoder = { it },
        role = role,
    )

    private fun givenVacantUnitAnd(resident: User) {
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null
        every { residencyRepository.findActiveByResident(resident.id) } returns null
    }

    private fun apartment(inBuildingId: BuildingId = buildingId) = Apartment.create(
        buildingId = inBuildingId,
        unitNumber = "1",
        floorNumber = 1,
        areaSquareMeters = java.math.BigDecimal.TEN,
        bedrooms = 1,
    )

    @Test
    fun `start links a resident to a vacant unit`() {
        val resident = user()
        givenVacantUnitAnd(resident)
        val saved = slot<Residency>()
        every { residencyRepository.save(capture(saved)) } answers { saved.captured }

        val residency = service.start(
            apartmentId,
            StartResidencyCommand(resident.id, TenancyType.OWNER_OCCUPIER),
            managerId,
        )

        assertEquals(apartmentId, residency.apartmentId)
        assertEquals(resident.id, residency.residentId)
        assertEquals(TenancyType.OWNER_OCCUPIER, residency.tenancy)
    }

    @Test
    fun `a unit cannot take a second current resident`() {
        val resident = user()
        val occupier = user()
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns
            Residency.start(apartmentId, occupier.id, TenancyType.TENANT)

        assertFailsWith<DomainConflictException> {
            service.start(apartmentId, StartResidencyCommand(resident.id, TenancyType.TENANT), managerId)
        }
        verify(exactly = 0) { residencyRepository.save(any()) }
    }

    @Test
    fun `a resident cannot occupy two units at once`() {
        val resident = user()
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null
        every { residencyRepository.findActiveByResident(resident.id) } returns
            Residency.start(ApartmentId.new(), resident.id, TenancyType.TENANT)

        assertFailsWith<DomainConflictException> {
            service.start(apartmentId, StartResidencyCommand(resident.id, TenancyType.TENANT), managerId)
        }
    }

    @Test
    fun `service staff cannot be registered as a unit resident`() {
        val worker = user(Role.STAFF)
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { userRepository.findById(worker.id) } returns worker

        assertFailsWith<DomainConflictException> {
            service.start(apartmentId, StartResidencyCommand(worker.id, TenancyType.TENANT), managerId)
        }
    }

    @Test
    fun `starting a residency in an unknown unit is rejected`() {
        val resident = user()
        every { apartmentRepository.findById(apartmentId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.start(apartmentId, StartResidencyCommand(resident.id, TenancyType.TENANT), managerId)
        }
    }

    @Test
    fun `invitation acceptance starts a residency without manager authorization`() {
        val resident = user()
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null
        every { residencyRepository.findActiveByResident(resident.id) } returns null
        every { residencyRepository.save(any()) } answers { firstArg() }

        val residency = service.startFromInvitation(
            apartmentId,
            buildingId,
            StartResidencyCommand(resident.id, TenancyType.TENANT),
        )

        assertEquals(resident.id, residency.residentId)
        verify(exactly = 0) { buildingAccess.requireManagerAccess(any(), any()) }
    }

    @Test
    fun `invitation acceptance rejects an apartment from another building`() {
        every { apartmentRepository.findById(apartmentId) } returns apartment(BuildingId.new())

        assertFailsWith<DomainConflictException> {
            service.startFromInvitation(
                apartmentId,
                buildingId,
                StartResidencyCommand(user().id, TenancyType.TENANT),
            )
        }
        verify(exactly = 0) { residencyRepository.save(any()) }
    }

    @Test
    fun `ending the current residency leaves the unit vacant`() {
        val resident = user()
        val residency = Residency.start(apartmentId, resident.id, TenancyType.TENANT)
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { residencyRepository.findActiveByApartment(apartmentId) } returns residency
        every { residencyRepository.save(any()) } answers { firstArg() }

        val ended = service.endCurrent(apartmentId, managerId)

        assertFalse(ended.active)
    }

    @Test
    fun `ending a residency of a vacant unit is rejected`() {
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null

        assertFailsWith<EntityNotFoundException> { service.endCurrent(apartmentId, managerId) }
    }

    @Test
    fun `getActiveByBuilding with a building id scopes the query to that building`() {
        val residency = Residency.start(apartmentId, user().id, TenancyType.TENANT)
        justRun { buildingAccess.requireManagerAccess(buildingId, managerId) }
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency)

        val residencies = service.getActiveByBuilding(buildingId, managerId)

        assertEquals(listOf(residency), residencies)
        verify(exactly = 0) { residencyRepository.findAllActive() }
    }

    @Test
    fun `getActiveByBuilding with no building id returns only the manager's building`() {
        val residency = Residency.start(apartmentId, user().id, TenancyType.TENANT)
        every { buildingAccess.managedBuildingId(managerId) } returns buildingId
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(residency)

        val residencies = service.getActiveByBuilding(null, managerId)

        assertEquals(listOf(residency), residencies)
        verify(exactly = 0) { residencyRepository.findAllActive() }
    }

    @Test
    fun `manager cannot list residencies from another building`() {
        val otherBuildingId = BuildingId.new()
        every {
            buildingAccess.requireManagerAccess(otherBuildingId, managerId)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.getActiveByBuilding(otherBuildingId, managerId)
        }
        verify(exactly = 0) { residencyRepository.findActiveByBuilding(any()) }
    }

    @Test
    fun `manager cannot start a residency in another building`() {
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        every {
            buildingAccess.requireManagerAccess(buildingId, managerId)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.start(apartmentId, StartResidencyCommand(user().id, TenancyType.TENANT), managerId)
        }
        verify(exactly = 0) { residencyRepository.save(any()) }
    }

    @Test
    fun `manager cannot read residency history from another building`() {
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        every {
            buildingAccess.requireManagerAccess(buildingId, managerId)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.getHistory(apartmentId, managerId) }
        verify(exactly = 0) { residencyRepository.findAllByApartment(any()) }
    }

    @Test
    fun `manager cannot end a residency in another building`() {
        every { apartmentRepository.findById(apartmentId) } returns apartment()
        every {
            buildingAccess.requireManagerAccess(buildingId, managerId)
        } throws DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.endCurrent(apartmentId, managerId) }
        verify(exactly = 0) { residencyRepository.findActiveByApartment(any()) }
    }
}
