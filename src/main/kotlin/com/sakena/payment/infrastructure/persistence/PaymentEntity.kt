package com.sakena.payment.infrastructure.persistence

import com.sakena.payment.domain.model.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 100)
    var transactionReference: String,

    @Column(name = "receipt_object_key", length = 500)
    var receiptObjectKey: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus,

    @Column(name = "paid_at", nullable = false, updatable = false)
    var paidAt: Instant,

    @Column(name = "reviewed_by")
    var reviewedBy: UUID?,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant?,

    @Column(name = "rejection_reason", length = 500)
    var rejectionReason: String?,
)
