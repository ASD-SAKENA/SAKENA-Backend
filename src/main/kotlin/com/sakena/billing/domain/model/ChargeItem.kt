package com.sakena.billing.domain.model

import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainValidationException
import java.math.BigDecimal
import java.time.Instant

/** What kind of cost a line represents — drives grouping in the UI and reports. */
enum class ChargeItemKind {
    /** The recurring monthly/quarterly charge every unit owes. */
    RECURRING_CHARGE,

    /** Running cost of a shared facility (pool, elevator, boiler room, …). */
    FACILITY_COST,

    /** An unexpected one-off expense billed on top of the recurring charge. */
    EXTRAORDINARY_EXPENSE,
}

/** How a cost line is split across the building's units. */
enum class CostAllocation {
    /** Every unit pays the same share. */
    EQUAL,

    /** Each unit pays proportionally to its area in square meters. */
    BY_AREA,

    /** One explicitly selected unit pays the full cost. */
    SPECIFIC_UNIT,
}

/**
 * ChargeItem aggregate root — one cost line inside a [ChargePeriod]. Items are
 * immutable once created; the manager removes and re-adds instead of editing,
 * which keeps issued invoices reproducible from their inputs.
 */
class ChargeItem private constructor(
    val id: ChargeItemId,
    val periodId: ChargePeriodId,
    val title: String,
    val amount: BigDecimal,
    val kind: ChargeItemKind,
    val allocation: CostAllocation,
    val targetApartmentId: ApartmentId?,
    val createdAt: Instant,
) {

    companion object {
        const val MAX_TITLE_LENGTH = 200

        fun create(
            periodId: ChargePeriodId,
            title: String,
            amount: BigDecimal,
            kind: ChargeItemKind,
            allocation: CostAllocation,
            targetApartmentId: ApartmentId? = null,
        ): ChargeItem =
            ChargeItem(
                id = ChargeItemId.new(),
                periodId = periodId,
                title = validateTitle(title),
                amount = validateAmount(amount),
                kind = kind,
                allocation = allocation,
                targetApartmentId = validateTarget(allocation, targetApartmentId),
                createdAt = Instant.now(),
            )

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: ChargeItemId,
            periodId: ChargePeriodId,
            title: String,
            amount: BigDecimal,
            kind: ChargeItemKind,
            allocation: CostAllocation,
            targetApartmentId: ApartmentId?,
            createdAt: Instant,
        ): ChargeItem = ChargeItem(
            id,
            periodId,
            title,
            amount,
            kind,
            allocation,
            targetApartmentId,
            createdAt,
        )

        private fun validateTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Charge item title must not be blank")
            if (trimmed.length > MAX_TITLE_LENGTH) {
                throw DomainValidationException("Charge item title must be at most $MAX_TITLE_LENGTH characters")
            }
            return trimmed
        }

        /**
         * Toman has no sub-unit, so a cost line is always a whole number.
         * Keeping fractions here would push them through the split and
         * invoice a unit for an amount it cannot pay.
         */
        private fun validateAmount(amount: BigDecimal): BigDecimal {
            if (amount <= BigDecimal.ZERO) {
                throw DomainValidationException("Charge item amount must be greater than zero")
            }
            if (amount.stripTrailingZeros().scale() > 0) {
                throw DomainValidationException("Charge item amount must be a whole number of Toman")
            }
            return amount.setScale(0)
        }

        private fun validateTarget(
            allocation: CostAllocation,
            targetApartmentId: ApartmentId?,
        ): ApartmentId? {
            if (allocation == CostAllocation.SPECIFIC_UNIT && targetApartmentId == null) {
                throw DomainValidationException(
                    "A specific-unit charge item must identify its target apartment",
                )
            }
            if (allocation != CostAllocation.SPECIFIC_UNIT && targetApartmentId != null) {
                throw DomainValidationException(
                    "Only a specific-unit charge item may identify a target apartment",
                )
            }
            return targetApartmentId
        }
    }
}
