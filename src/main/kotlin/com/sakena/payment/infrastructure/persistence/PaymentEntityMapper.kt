package com.sakena.payment.infrastructure.persistence

import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentId
import com.sakena.user.domain.UserId

/** Translates between the domain aggregate and its JPA representation. */
internal object PaymentEntityMapper {

    fun toEntity(payment: Payment): PaymentEntity =
        PaymentEntity(
            id = payment.id.value,
            payerId = payment.payerId.value,
            title = payment.title,
            amount = payment.amount,
            paidAt = payment.paidAt,
        )

    fun toDomain(entity: PaymentEntity): Payment =
        Payment.reconstitute(
            id = PaymentId(entity.id),
            payerId = UserId(entity.payerId),
            title = entity.title,
            amount = entity.amount,
            paidAt = entity.paidAt,
        )
}
