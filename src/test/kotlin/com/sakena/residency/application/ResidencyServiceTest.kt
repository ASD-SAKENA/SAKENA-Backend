package com.sakena.residency.application

import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.application.command.StartResidencyCommand
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserRepository
import io.mockk.every
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
    private val userRepository = mockk<UserRepository>()
    private val service = ResidencyService(
        residencyRepository,
        apartmentRepository,
        buildingRepository,
        userRepository,
    )

    private val apartmentId = ApartmentId.new()

    private fun user(role: Role = Role.RESIDENT) = User.register(
        username = "resident-${role.name.lowercase()}",
        email = "${role.name.lowercase()}@sakena.test",
        rawPassword = "password123",
        passwordEncoder = { it },
        role = role,
    )

    private fun givenVacantUnitAnd(resident: User) {
        every { apartmentRepository.existsById(apartmentId) } returns true
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
        )

        assertEquals(apartmentId, residency.apartmentId)
        assertEquals(resident.id, residency.residentId)
        assertEquals(TenancyType.OWNER_OCCUPIER, residency.tenancy)
    }

    @Test
    fun `a unit cannot take a second current resident`() {
        val resident = user()
        val occupier = user()
        every { apartmentRepository.existsById(apartmentId) } returns true
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns
            Residency.start(apartmentId, occupier.id, TenancyType.TENANT)

        assertFailsWith<DomainConflictException> {
            service.start(apartmentId, StartResidencyCommand(resident.id, TenancyType.TENANT))
        }
        verify(exactly = 0) { residencyRepository.save(any()) }
    }

    @Test
    fun `a resident cannot occupy two units at once`() {
        val resident = user()
        every { apartmentRepository.existsById(apartmentId) } returns true
        every { userRepository.findById(resident.id) } returns resident
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null
        every { residencyRepository.findActiveByResident(resident.id) } returns
            Residency.start(ApartmentId.new(), resident.id, TenancyType.TENANT)

        assertFailsWith<DomainConflictException> {
            service.start(apartmentId, StartResidencyCommand(resident.id, TenancyType.TENANT))
        }
    }

    @Test
    fun `service staff cannot be registered as a unit resident`() {
        val worker = user(Role.STAFF)
        every { apartmentRepository.existsById(apartmentId) } returns true
        every { userRepository.findById(worker.id) } returns worker

        assertFailsWith<DomainConflictException> {
            service.start(apartmentId, StartResidencyCommand(worker.id, TenancyType.TENANT))
        }
    }

    @Test
    fun `starting a residency in an unknown unit is rejected`() {
        val resident = user()
        every { apartmentRepository.existsById(apartmentId) } returns false

        assertFailsWith<EntityNotFoundException> {
            service.start(apartmentId, StartResidencyCommand(resident.id, TenancyType.TENANT))
        }
    }

    @Test
    fun `ending the current residency leaves the unit vacant`() {
        val resident = user()
        val residency = Residency.start(apartmentId, resident.id, TenancyType.TENANT)
        every { residencyRepository.findActiveByApartment(apartmentId) } returns residency
        every { residencyRepository.save(any()) } answers { firstArg() }

        val ended = service.endCurrent(apartmentId)

        assertFalse(ended.active)
    }

    @Test
    fun `ending a residency of a vacant unit is rejected`() {
        every { residencyRepository.findActiveByApartment(apartmentId) } returns null

        assertFailsWith<EntityNotFoundException> { service.endCurrent(apartmentId) }
    }
}
