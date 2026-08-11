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
            transactionReference = payment.transactionReference,
            receiptObjectKey = payment.receiptObjectKey,
            status = payment.status,
            paidAt = payment.paidAt,
            reviewedBy = payment.reviewedBy?.value,
            reviewedAt = payment.reviewedAt,
            rejectionReason = payment.rejectionReason,
        )

    fun toDomain(entity: PaymentEntity): Payment =
        Payment.reconstitute(
            id = PaymentId(entity.id),
            payerId = UserId(entity.payerId),
            title = entity.title,
            amount = entity.amount,
            transactionReference = entity.transactionReference,
            receiptObjectKey = entity.receiptObjectKey,
            status = entity.status,
            paidAt = entity.paidAt,
            reviewedBy = entity.reviewedBy?.let(::UserId),
            reviewedAt = entity.reviewedAt,
            rejectionReason = entity.rejectionReason,
        )
}
