package com.sakena.payment.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentJpaRepository : JpaRepository<PaymentEntity, UUID> {
    fun findAllByPayerIdOrderByPaidAtDesc(payerId: UUID): List<PaymentEntity>
}
