package com.sakena.billing.domain

import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.property.domain.model.ApartmentId
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CostAllocationPolicyTest {

    private val periodId = ChargePeriodId.new()
    private val unitA = BillableUnit(ApartmentId.new(), BigDecimal("50"))
    private val unitB = BillableUnit(ApartmentId.new(), BigDecimal("100"))
    private val unitC = BillableUnit(ApartmentId.new(), BigDecimal("50"))

    private fun item(
        amount: String,
        allocation: CostAllocation,
        targetApartmentId: ApartmentId? = null,
    ) = ChargeItem.create(
        periodId = periodId,
        title = "Charge",
        amount = BigDecimal(amount),
        kind = ChargeItemKind.RECURRING_CHARGE,
        allocation = allocation,
        targetApartmentId = targetApartmentId,
    )

    @Test
    fun `equal allocation splits the amount evenly`() {
        val shares = CostAllocationPolicy.allocate(
            listOf(item("900000", CostAllocation.EQUAL)),
            listOf(unitA, unitB, unitC),
        )

        assertEquals(0, BigDecimal("300000").compareTo(shares.getValue(unitA.apartmentId)))
        assertEquals(0, BigDecimal("300000").compareTo(shares.getValue(unitB.apartmentId)))
    }

    @Test
    fun `area allocation is proportional to each unit's area`() {
        val shares = CostAllocationPolicy.allocate(
            listOf(item("200000", CostAllocation.BY_AREA)),
            listOf(unitA, unitB, unitC),
        )

        assertEquals(0, BigDecimal("50000").compareTo(shares.getValue(unitA.apartmentId)))
        assertEquals(0, BigDecimal("100000").compareTo(shares.getValue(unitB.apartmentId)))
    }

    @Test
    fun `rounding remainder keeps the split exact`() {
        val amount = BigDecimal("100")
        val shares = CostAllocationPolicy.allocate(
            listOf(item("100", CostAllocation.EQUAL)),
            listOf(unitA, unitB, unitC),
        )

        val total = shares.values.fold(BigDecimal.ZERO) { acc, value -> acc + value }
        assertEquals(0, amount.compareTo(total))
    }

    @Test
    fun `multiple cost lines accumulate per unit`() {
        val shares = CostAllocationPolicy.allocate(
            listOf(
                item("300000", CostAllocation.EQUAL),
                item("200000", CostAllocation.BY_AREA),
            ),
            listOf(unitA, unitB, unitC),
        )

        // 100000 equal share + 50000 area share for the 50m² unit.
        assertEquals(0, BigDecimal("150000").compareTo(shares.getValue(unitA.apartmentId)))
    }

    @Test
    fun `specific-unit allocation charges only its target apartment`() {
        val shares = CostAllocationPolicy.allocate(
            listOf(item("275000", CostAllocation.SPECIFIC_UNIT, unitB.apartmentId)),
            listOf(unitA, unitB, unitC),
        )

        assertEquals(BigDecimal.ZERO, shares.getValue(unitA.apartmentId))
        assertEquals(BigDecimal("275000"), shares.getValue(unitB.apartmentId))
        assertEquals(BigDecimal.ZERO, shares.getValue(unitC.apartmentId))
    }

    @Test
    fun `specific-unit allocation rejects a target outside the building`() {
        assertFailsWith<com.sakena.shared.domain.DomainConflictException> {
            CostAllocationPolicy.allocate(
                listOf(item("275000", CostAllocation.SPECIFIC_UNIT, ApartmentId.new())),
                listOf(unitA, unitB, unitC),
            )
        }
    }

    @Test
    fun `allocation without units is rejected`() {
        assertFailsWith<com.sakena.shared.domain.DomainConflictException> {
            CostAllocationPolicy.allocate(listOf(item("1000", CostAllocation.EQUAL)), emptyList())
        }
    }

    @Test
    fun `an uneven split produces whole Toman, never fractions`() {
        // The bug: 100,000 across three units used to invoice 33,333.33 each,
        // an amount no resident can actually pay.
        val shares = CostAllocationPolicy.allocate(
            listOf(item("100000", CostAllocation.EQUAL)),
            listOf(unitA, unitB, unitC),
        )

        for (share in shares.values) {
            assertEquals(0, share.stripTrailingZeros().compareTo(share.toBigInteger().toBigDecimal()), "share $share is not whole")
        }
        assertEquals(0, BigDecimal("33333").compareTo(shares.getValue(unitA.apartmentId)))
        assertEquals(0, BigDecimal("33333").compareTo(shares.getValue(unitB.apartmentId)))
        // The last unit absorbs the leftover so the total stays exact.
        assertEquals(0, BigDecimal("33334").compareTo(shares.getValue(unitC.apartmentId)))
    }

    @Test
    fun `an uneven area split also stays whole and exact`() {
        val amount = BigDecimal("100000")
        val shares = CostAllocationPolicy.allocate(
            listOf(item("100000", CostAllocation.BY_AREA)),
            listOf(unitA, unitB, unitC),
        )

        for (share in shares.values) {
            assertEquals(0, share.stripTrailingZeros().compareTo(share.toBigInteger().toBigDecimal()), "share $share is not whole")
        }
        val total = shares.values.fold(BigDecimal.ZERO) { acc, value -> acc + value }
        assertEquals(0, amount.compareTo(total))
    }

    @Test
    fun `a cost line with a fraction of a Toman is refused`() {
        assertFailsWith<com.sakena.shared.domain.DomainValidationException> {
            item("1000.50", CostAllocation.EQUAL)
        }
    }
}
