package com.sakena.billing.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

/** Value object identifying a [ChargePeriod] aggregate. */
@JvmInline
value class ChargePeriodId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ChargePeriodId = ChargePeriodId(UUID.randomUUID())

        fun from(raw: String): ChargePeriodId =
            try {
                ChargePeriodId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid charge period id")
            }
    }
}

/** Value object identifying a [ChargeItem] aggregate. */
@JvmInline
value class ChargeItemId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ChargeItemId = ChargeItemId(UUID.randomUUID())

        fun from(raw: String): ChargeItemId =
            try {
                ChargeItemId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid charge item id")
            }
    }
}

/** Value object identifying a [UnitInvoice] aggregate. */
@JvmInline
value class UnitInvoiceId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): UnitInvoiceId = UnitInvoiceId(UUID.randomUUID())

        fun from(raw: String): UnitInvoiceId =
            try {
                UnitInvoiceId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid invoice id")
            }
    }
}
