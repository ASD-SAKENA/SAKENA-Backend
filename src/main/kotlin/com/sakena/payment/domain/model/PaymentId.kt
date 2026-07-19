package com.sakena.payment.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

/**
 * Value object identifying a [Payment] aggregate. Wrapping the raw [UUID]
 * keeps the type system honest — a `PaymentId` can never be mixed up with
 * some other id.
 */
@JvmInline
value class PaymentId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): PaymentId = PaymentId(UUID.randomUUID())

        fun from(raw: String): PaymentId =
            try {
                PaymentId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid payment id")
            }
    }
}
