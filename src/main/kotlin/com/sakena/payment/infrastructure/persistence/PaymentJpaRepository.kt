package com.sakena.payment.infrastructure.persistence

import com.sakena.payment.domain.model.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentJpaRepository : JpaRepository<PaymentEntity, UUID> {
    fun existsByTransactionReference(transactionReference: String): Boolean

    fun findAllByPayerIdAndStatusOrderByPaidAtDesc(
        payerId: UUID,
        status: PaymentStatus,
    ): List<PaymentEntity>

    fun findAllByPayerIdOrderByPaidAtDesc(payerId: UUID): List<PaymentEntity>

    fun findAllByBuildingIdAndStatusOrderByPaidAtDesc(
        buildingId: UUID,
        status: PaymentStatus,
    ): List<PaymentEntity>
}
