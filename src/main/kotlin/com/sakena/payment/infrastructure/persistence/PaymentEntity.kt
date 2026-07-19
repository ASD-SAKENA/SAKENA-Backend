package com.sakena.payment.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA persistence model. Deliberately separate from the domain
 * [com.sakena.payment.domain.model.Payment] so database/ORM concerns never
 * leak into the domain.
 */
@Entity
@Table(name = "payments")
class PaymentEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "payer_id", nullable = false, updatable = false)
    var payerId: UUID,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    var amount: BigDecimal,

    @Column(name = "paid_at", nullable = false, updatable = false)
    var paidAt: Instant,
)
