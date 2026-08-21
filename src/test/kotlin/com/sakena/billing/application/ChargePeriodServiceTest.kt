package com.sakena.billing.application

import com.sakena.billing.application.command.AddChargeItemCommand
import com.sakena.billing.application.command.CreateChargePeriodCommand
import com.sakena.billing.application.command.UpdateChargePeriodCommand
import com.sakena.billing.domain.ChargeItemRepository
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.BuildingId
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChargePeriodServiceTest {

    private val periodRepository = mockk<ChargePeriodRepository>()
    private val itemRepository = mockk<ChargeItemRepository>()
    private val invoiceRepository = mockk<UnitInvoiceRepository>()
    private val serviceChargeRepository = mockk<ServiceChargeRepository>()
    private val apartmentRepository = mockk<ApartmentRepository>()
    private val buildingAccess = mockk<BuildingAccess>()
    private val service = ChargePeriodService(
        periodRepository,
        itemRepository,
        invoiceRepository,
        serviceChargeRepository,
        apartmentRepository,
        buildingAccess,
    )

    private val managerId = UserId.generate()
    private val buildingId = BuildingId.new()

    @Test
    fun `create checks that the manager owns the requested building`() {
        val command = createCommand(buildingId)
        every { buildingAccess.requireManagerAccess(buildingId, managerId) } throws
            DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.create(command, managerId) }

        verify(exactly = 0) { periodRepository.save(any()) }
    }

    @Test
    fun `list always queries only the manager's building`() {
        val period = period(buildingId)
        every { buildingAccess.managedBuildingId(managerId) } returns buildingId
        every { periodRepository.findAll(buildingId) } returns listOf(period)

        val result = service.getAll(null, managerId)

        assertEquals(listOf(period), result)
        verify(exactly = 1) { periodRepository.findAll(buildingId) }
        verify(exactly = 0) { periodRepository.findAll(null) }
    }

    @Test
    fun `list rejects an explicit building outside the manager's scope`() {
        every { buildingAccess.managedBuildingId(managerId) } returns buildingId

        assertFailsWith<DomainForbiddenException> {
            service.getAll(BuildingId.new(), managerId)
        }

        verify(exactly = 0) { periodRepository.findAll(any()) }
    }

    @Test
    fun `every period-bound operation checks manager ownership`() {
        val period = period(buildingId)
        val itemId = ChargeItemId.new()
        every { periodRepository.findById(period.id) } returns period
        every { buildingAccess.requireManagerAccess(buildingId, managerId) } throws
            DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> { service.getById(period.id, managerId) }
        assertFailsWith<DomainForbiddenException> { service.getItems(period.id, managerId) }
        assertFailsWith<DomainForbiddenException> {
            service.update(
                period.id,
                UpdateChargePeriodCommand("Updated", period.startsOn, period.endsOn),
                managerId,
            )
        }
        assertFailsWith<DomainForbiddenException> { service.close(period.id, managerId) }
        assertFailsWith<DomainForbiddenException> { service.delete(period.id, managerId) }
        assertFailsWith<DomainForbiddenException> {
            service.addItem(
                period.id,
                AddChargeItemCommand(
                    title = "Monthly charge",
                    amount = BigDecimal("100"),
                    kind = ChargeItemKind.RECURRING_CHARGE,
                    allocation = CostAllocation.EQUAL,
                ),
                managerId,
            )
        }
        assertFailsWith<DomainForbiddenException> {
            service.removeItem(period.id, itemId, managerId)
        }

        verify(exactly = 0) { periodRepository.save(any()) }
        verify(exactly = 0) { periodRepository.deleteById(any()) }
        verify(exactly = 0) { itemRepository.save(any()) }
        verify(exactly = 0) { itemRepository.deleteById(any()) }
    }

    @Test
    fun `specific unit charge rejects an apartment from another building`() {
        val period = period(buildingId)
        val otherApartment = Apartment.create(
            buildingId = BuildingId.new(),
            unitNumber = "B-1",
            floorNumber = 1,
            areaSquareMeters = BigDecimal("50"),
            bedrooms = 1,
        )
        every { periodRepository.findById(period.id) } returns period
        every { buildingAccess.requireManagerAccess(buildingId, managerId) } returns Unit
        every { apartmentRepository.findById(otherApartment.id) } returns otherApartment

        assertFailsWith<DomainConflictException> {
            service.addItem(
                period.id,
                AddChargeItemCommand(
                    title = "Repair",
                    amount = BigDecimal("100"),
                    kind = ChargeItemKind.EXTRAORDINARY_EXPENSE,
                    allocation = CostAllocation.SPECIFIC_UNIT,
                    targetApartmentId = otherApartment.id,
                ),
                managerId,
            )
        }

        verify(exactly = 0) { itemRepository.save(any()) }
    }

    @Test
    fun `pending service charges come from the manager's building only`() {
        val charge = ServiceCharge.create(
            sourceServiceRequestId = ServiceRequestId.generate(),
            buildingId = buildingId,
            title = "تعمیر آسانسور",
            amount = BigDecimal("250000"),
            target = ServiceChargeTarget.SPECIFIC_UNIT,
            targetApartmentId = Apartment.create(
                buildingId = buildingId,
                unitNumber = "12",
                floorNumber = 3,
                areaSquareMeters = BigDecimal("90"),
                bedrooms = 2,
            ).id,
        )
        every { buildingAccess.managedBuildingId(managerId) } returns buildingId
        every { serviceChargeRepository.findPendingByBuilding(buildingId) } returns listOf(charge)

        assertEquals(listOf(charge), service.getPendingServiceCharges(managerId))
        verify(exactly = 1) { serviceChargeRepository.findPendingByBuilding(buildingId) }
    }

    private fun createCommand(id: BuildingId) = CreateChargePeriodCommand(
        buildingId = id,
        title = "Monthly charge",
        type = ChargePeriodType.MONTHLY,
        startsOn = LocalDate.of(2026, 8, 1),
        endsOn = LocalDate.of(2026, 8, 31),
    )

    private fun period(id: BuildingId): ChargePeriod = ChargePeriod.create(
        buildingId = id,
        title = "Monthly charge",
        type = ChargePeriodType.MONTHLY,
        startsOn = LocalDate.of(2026, 8, 1),
        endsOn = LocalDate.of(2026, 8, 31),
    )
}
