package com.sakena.billing.domain

import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainValidationException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChargeItemTest {

    @Test
    fun `specific-unit allocation requires a target apartment`() {
        assertFailsWith<DomainValidationException> {
            create(CostAllocation.SPECIFIC_UNIT)
        }
    }

    @Test
    fun `shared allocation rejects a target apartment`() {
        assertFailsWith<DomainValidationException> {
            create(CostAllocation.EQUAL, ApartmentId.new())
        }
    }

    @Test
    fun `specific-unit allocation keeps its target apartment`() {
        val targetApartmentId = ApartmentId.new()

        val item = create(CostAllocation.SPECIFIC_UNIT, targetApartmentId)

        assertEquals(targetApartmentId, item.targetApartmentId)
    }

    private fun create(
        allocation: CostAllocation,
        targetApartmentId: ApartmentId? = null,
    ): ChargeItem = ChargeItem.create(
        periodId = ChargePeriodId.new(),
        title = "Service cost",
        amount = BigDecimal("250000"),
        kind = ChargeItemKind.EXTRAORDINARY_EXPENSE,
        allocation = allocation,
        targetApartmentId = targetApartmentId,
    )
}
