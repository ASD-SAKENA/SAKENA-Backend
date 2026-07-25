package com.sakena.billing.domain

import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.CostAllocation
import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainConflictException
import java.math.BigDecimal
import java.math.RoundingMode

/** A unit the cost is divided between — only what allocation actually needs. */
data class BillableUnit(
    val apartmentId: ApartmentId,
    val areaSquareMeters: BigDecimal,
)

/**
 * Splits charge items across the building's units. Pure domain logic: the
 * rounding remainder always lands on the last unit so the allocated shares add
 * up to exactly the item amount — a building must never over- or under-bill.
 */
object CostAllocationPolicy {

    private const val SCALE = 2

    fun allocate(items: List<ChargeItem>, units: List<BillableUnit>): Map<ApartmentId, BigDecimal> {
        if (units.isEmpty()) {
            throw DomainConflictException("Cannot allocate charges: the building has no units")
        }
        val totals = units.associate { it.apartmentId to BigDecimal.ZERO }.toMutableMap()
        for (item in items) {
            val shares = when (item.allocation) {
                CostAllocation.EQUAL -> splitEqually(item.amount, units)
                CostAllocation.BY_AREA -> splitByArea(item.amount, units)
            }
            for ((apartmentId, share) in shares) {
                totals[apartmentId] = totals.getValue(apartmentId) + share
            }
        }
        return totals
    }

    private fun splitEqually(
        amount: BigDecimal,
        units: List<BillableUnit>,
    ): Map<ApartmentId, BigDecimal> {
        val share = amount.divide(BigDecimal(units.size), SCALE, RoundingMode.DOWN)
        return withRemainderOnLast(amount, units) { share }
    }

    private fun splitByArea(
        amount: BigDecimal,
        units: List<BillableUnit>,
    ): Map<ApartmentId, BigDecimal> {
        val totalArea = units.fold(BigDecimal.ZERO) { acc, unit -> acc + unit.areaSquareMeters }
        // Units without a recorded area would make the ratio undefined — fall back to an even split.
        if (totalArea <= BigDecimal.ZERO) return splitEqually(amount, units)
        return withRemainderOnLast(amount, units) { unit ->
            amount.multiply(unit.areaSquareMeters).divide(totalArea, SCALE, RoundingMode.DOWN)
        }
    }

    private fun withRemainderOnLast(
        amount: BigDecimal,
        units: List<BillableUnit>,
        shareOf: (BillableUnit) -> BigDecimal,
    ): Map<ApartmentId, BigDecimal> {
        val shares = LinkedHashMap<ApartmentId, BigDecimal>(units.size)
        var allocated = BigDecimal.ZERO
        for ((index, unit) in units.withIndex()) {
            val share = if (index == units.lastIndex) {
                amount - allocated
            } else {
                shareOf(unit).also { allocated += it }
            }
            shares[unit.apartmentId] = share
        }
        return shares
    }
}
