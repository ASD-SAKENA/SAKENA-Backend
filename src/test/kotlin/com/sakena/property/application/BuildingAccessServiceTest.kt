package com.sakena.property.application

import com.sakena.membership.domain.StaffBuildingMembershipRepository
import com.sakena.membership.domain.model.StaffBuildingMembership
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.Building
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildingAccessServiceTest {

    private val buildingRepository = mockk<BuildingRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val staffMembershipRepository = mockk<StaffBuildingMembershipRepository>()
    private val service = BuildingAccessService(
        buildingRepository,
        residencyRepository,
        apartmentRepository,
        staffMembershipRepository,
    )

    @Test
    fun `resolves the building assigned to a manager`() {
        val managerId = UserId.generate()
        val building = Building.create("Tower", "Address", managerId)
        every { buildingRepository.findByManagerId(managerId) } returns building

        assertEquals(building.id, service.managedBuildingId(managerId))
    }

    @Test
    fun `resolves a resident building through the active apartment`() {
        val residentId = UserId.generate()
        val building = Building.create("Tower", "Address", UserId.generate())
        val apartment = Apartment.create(
            building.id,
            "101",
            1,
            BigDecimal("80.00"),
            2,
        )
        val residency = Residency.start(apartment.id, residentId, TenancyType.TENANT)
        every { residencyRepository.findActiveByResident(residentId) } returns residency
        every { apartmentRepository.findById(apartment.id) } returns apartment

        assertEquals(building.id, service.residentBuildingId(residentId))
    }

    @Test
    fun `rejects access when the manager owns another building`() {
        val managerId = UserId.generate()
        val building = Building.create("Tower", "Address", managerId)
        every { buildingRepository.findByManagerId(managerId) } returns building

        assertFailsWith<DomainForbiddenException> {
            service.requireManagerAccess(Building.create("Other", "Other", UserId.generate()).id, managerId)
        }
    }

    @Test
    fun `resolves the building assigned to staff`() {
        val staffId = UserId.generate()
        val building = Building.create("Tower", "Address", UserId.generate())
        val membership = StaffBuildingMembership.create(staffId, building.id)
        every { staffMembershipRepository.findByStaffId(staffId) } returns membership

        assertEquals(building.id, service.staffBuildingId(staffId))
    }

    @Test
    fun `rejects staff access to another building`() {
        val staffId = UserId.generate()
        val membership = StaffBuildingMembership.create(staffId, Building.create("Tower", "Address").id)
        every { staffMembershipRepository.findByStaffId(staffId) } returns membership

        assertFailsWith<DomainForbiddenException> {
            service.requireStaffAccess(Building.create("Other", "Other").id, staffId)
        }
    }
}
