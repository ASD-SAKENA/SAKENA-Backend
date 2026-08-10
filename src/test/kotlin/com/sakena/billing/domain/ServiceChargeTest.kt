package com.sakena.billing.domain

import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceChargeTest {

    private val serviceRequestId = ServiceRequestId.generate()
    private val buildingId = BuildingId.new()

    @Test
    fun `all-units charge starts pending without a target apartment`() {
        val charge = charge(ServiceChargeTarget.ALL_UNITS)

        assertEquals("Water pump repair", charge.title)
        assertTrue(charge.pending)
        assertNull(charge.targetApartmentId)
        assertNull(charge.attachedPeriodId)
        assertNull(charge.attachedAt)
    }

    @Test
    fun `specific-unit charge requires exactly one target apartment`() {
        val apartmentId = ApartmentId.new()
        val specific = charge(ServiceChargeTarget.SPECIFIC_UNIT, apartmentId)

        assertEquals(apartmentId, specific.targetApartmentId)
        assertFailsWith<DomainValidationException> {
            charge(ServiceChargeTarget.SPECIFIC_UNIT, targetApartmentId = null)
        }
        assertFailsWith<DomainValidationException> {
            charge(ServiceChargeTarget.ALL_UNITS, targetApartmentId = apartmentId)
        }
    }

    @Test
    fun `charge requires a title and positive amount`() {
        assertFailsWith<DomainValidationException> {
            charge(ServiceChargeTarget.ALL_UNITS, title = " ")
        }
        assertFailsWith<DomainValidationException> {
            charge(ServiceChargeTarget.ALL_UNITS, amount = BigDecimal.ZERO)
        }
        assertFailsWith<DomainValidationException> {
            charge(ServiceChargeTarget.ALL_UNITS, amount = BigDecimal("-1"))
        }
    }

    @Test
    fun `charge can attach to a period only once`() {
        val charge = charge(ServiceChargeTarget.ALL_UNITS)
        val periodId = ChargePeriodId.new()

        charge.attachTo(periodId)

        assertTrue(!charge.pending)
        assertEquals(periodId, charge.attachedPeriodId)
        assertNotNull(charge.attachedAt)
        assertFailsWith<DomainConflictException> {
            charge.attachTo(ChargePeriodId.new())
        }
    }

    @Test
    fun `all-units charge creates an equally allocated cost line`() {
        val periodId = ChargePeriodId.new()

        val item = charge(ServiceChargeTarget.ALL_UNITS).createChargeItemFor(periodId)

        assertEquals(periodId, item.periodId)
        assertEquals(CostAllocation.EQUAL, item.allocation)
        assertNull(item.targetApartmentId)
    }

    @Test
    fun `specific-unit charge creates a cost line for its apartment`() {
        val periodId = ChargePeriodId.new()
        val apartmentId = ApartmentId.new()

        val item = charge(
            ServiceChargeTarget.SPECIFIC_UNIT,
            apartmentId,
        ).createChargeItemFor(periodId)

        assertEquals(CostAllocation.SPECIFIC_UNIT, item.allocation)
        assertEquals(apartmentId, item.targetApartmentId)
    }

    private fun charge(
        target: ServiceChargeTarget,
        targetApartmentId: ApartmentId? = null,
        title: String = "  Water pump repair  ",
        amount: BigDecimal = BigDecimal("250.00"),
    ) = ServiceCharge.create(
        sourceServiceRequestId = serviceRequestId,
        buildingId = buildingId,
        title = title,
        amount = amount,
        target = target,
        targetApartmentId = targetApartmentId,
    )
}
