package com.sakena.payment.infrastructure.persistence

import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Component

/**
 * Adapter implementing the domain [PaymentRepository] port on top of Spring
 * Data JPA. This is the only place that knows about [PaymentEntity] and
 * [PaymentJpaRepository].
 */
@Component
class PaymentRepositoryAdapter(
    private val jpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val saved = jpaRepository.save(PaymentEntityMapper.toEntity(payment))
        return PaymentEntityMapper.toDomain(saved)
    }

    override fun findAllByPayerNewestFirst(payerId: UserId): List<Payment> =
        jpaRepository.findAllByPayerIdOrderByPaidAtDesc(payerId.value)
            .map(PaymentEntityMapper::toDomain)
}
