package com.sakena.billing.infrastructure.persistence

import com.sakena.IntegrationTest
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.Building
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceChargeRepositoryIntegrationTest(
    @Autowired private val serviceChargeRepository: ServiceChargeRepository,
    @Autowired private val chargePeriodRepository: ChargePeriodRepository,
    @Autowired private val serviceRequestRepository: ServiceRequestRepository,
    @Autowired private val buildingRepository: BuildingRepository,
    @Autowired private val apartmentRepository: ApartmentRepository,
) : IntegrationTest() {

    @Test
    fun `pending all-units charge survives a database round trip`() {
        val fixture = fixture()
        val charge = serviceCharge(
            fixture = fixture,
            target = ServiceChargeTarget.ALL_UNITS,
        )

        serviceChargeRepository.save(charge)

        val byId = serviceChargeRepository.findById(charge.id)
        val bySource = serviceChargeRepository.findBySourceServiceRequestId(fixture.request.id)
        val pending = serviceChargeRepository.findPendingByBuilding(fixture.building.id)
        assertEquals(charge.id, byId?.id)
        assertEquals(charge.id, bySource?.id)
        assertEquals(ServiceChargeTarget.ALL_UNITS, byId?.target)
        assertTrue(byId?.pending == true)
        assertTrue(pending.any { it.id == charge.id })
    }

    @Test
    fun `specific-unit charge retains its target and attachment`() {
        val fixture = fixture()
        val period = chargePeriodRepository.save(
            ChargePeriod.create(
                buildingId = fixture.building.id,
                title = "Next charge period",
                type = ChargePeriodType.MONTHLY,
                startsOn = LocalDate.of(2026, 9, 1),
                endsOn = LocalDate.of(2026, 10, 1),
            ),
        )
        val charge = serviceCharge(
            fixture = fixture,
            target = ServiceChargeTarget.SPECIFIC_UNIT,
        )
        charge.attachTo(period.id)

        serviceChargeRepository.save(charge)
        val reloaded = serviceChargeRepository.findById(charge.id)

        assertEquals(fixture.apartment.id, reloaded?.targetApartmentId)
        assertEquals(period.id, reloaded?.attachedPeriodId)
        assertNotNull(reloaded?.attachedAt)
        assertTrue(reloaded?.pending == false)
        assertTrue(
            serviceChargeRepository.findPendingByBuilding(fixture.building.id)
                .none { it.id == charge.id },
        )
    }

    @Test
    fun `source service request can be billed only once`() {
        val fixture = fixture()
        serviceChargeRepository.save(
            serviceCharge(fixture, ServiceChargeTarget.ALL_UNITS),
        )

        assertFailsWith<DataIntegrityViolationException> {
            serviceChargeRepository.save(
                serviceCharge(fixture, ServiceChargeTarget.ALL_UNITS),
            )
        }
    }

    private fun fixture(): Fixture {
        val suffix = UUID.randomUUID().toString().take(8)
        val building = buildingRepository.save(
            Building.create("Service charge $suffix", "Address $suffix"),
        )
        val apartment = apartmentRepository.save(
            Apartment.create(
                buildingId = building.id,
                unitNumber = "UNIT-$suffix",
                floorNumber = 1,
                areaSquareMeters = BigDecimal("90"),
                bedrooms = 2,
            ),
        )
        val request = serviceRequestRepository.save(
            ServiceRequest.create(
                title = "Repair water pump",
                description = "The main water pump needs repair",
                location = "Basement",
                createdBy = UserId.generate(),
                categoryGroup = ServiceCategoryGroup.FACILITIES,
                subCategory = ServiceSubCategory.PLUMBING,
                requestingApartmentId = apartment.id,
            ),
        )
        return Fixture(building, apartment, request)
    }

    private fun serviceCharge(
        fixture: Fixture,
        target: ServiceChargeTarget,
    ): ServiceCharge = ServiceCharge.create(
        sourceServiceRequestId = fixture.request.id,
        buildingId = fixture.building.id,
        title = "Water pump repair",
        amount = BigDecimal("250.00"),
        target = target,
        targetApartmentId = fixture.apartment.id.takeIf {
            target == ServiceChargeTarget.SPECIFIC_UNIT
        },
    )

    private data class Fixture(
        val building: Building,
        val apartment: Apartment,
        val request: ServiceRequest,
    )
}
