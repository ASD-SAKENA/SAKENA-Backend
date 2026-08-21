package com.sakena.payment.infrastructure.persistence

import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentId
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import org.springframework.data.repository.findByIdOrNull
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

    override fun findById(id: PaymentId): Payment? =
        jpaRepository.findByIdOrNull(id.value)?.let(PaymentEntityMapper::toDomain)

    override fun existsByTransactionReference(transactionReference: String): Boolean =
        jpaRepository.existsByTransactionReference(transactionReference)

    override fun existsPendingForInvoice(invoiceId: UnitInvoiceId): Boolean =
        jpaRepository.existsByInvoiceIdAndStatus(invoiceId.value, PaymentStatus.PENDING)

    override fun findAllByPayerNewestFirst(payerId: UserId): List<Payment> =
        jpaRepository.findAllByPayerIdAndStatusOrderByPaidAtDesc(
            payerId.value,
            PaymentStatus.CONFIRMED,
        )
            .map(PaymentEntityMapper::toDomain)

    override fun findAllSubmissionsByPayerNewestFirst(payerId: UserId): List<Payment> =
        jpaRepository.findAllByPayerIdOrderByPaidAtDesc(payerId.value)
            .map(PaymentEntityMapper::toDomain)

    override fun findAllPendingByBuildingNewestFirst(buildingId: BuildingId): List<Payment> =
        jpaRepository.findAllByBuildingIdAndStatusOrderByPaidAtDesc(
            buildingId.value,
            PaymentStatus.PENDING,
        )
            .map(PaymentEntityMapper::toDomain)

    override fun findAllByBuildingNewestFirst(buildingId: BuildingId): List<Payment> =
        jpaRepository.findAllByBuildingIdOrderByPaidAtDesc(buildingId.value)
            .map(PaymentEntityMapper::toDomain)
}
