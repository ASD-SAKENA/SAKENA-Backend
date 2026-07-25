package com.sakena.billing.infrastructure.persistence

import com.sakena.billing.domain.model.ChargeItemKind
import com.sakena.billing.domain.model.ChargePeriodStatus
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.CostAllocation
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA persistence models for the billing context. Deliberately separate from
 * the domain aggregates so database/ORM concerns never leak into the domain.
 */
@Entity
@Table(name = "charge_periods")
class ChargePeriodEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "building_id", nullable = false, updatable = false)
    var buildingId: UUID,

    @Column(name = "title", nullable = false, length = 150)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: ChargePeriodType,

    @Column(name = "starts_on", nullable = false)
    var startsOn: LocalDate,

    @Column(name = "ends_on", nullable = false)
    var endsOn: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ChargePeriodStatus,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

@Entity
@Table(name = "charge_items")
class ChargeItemEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "period_id", nullable = false, updatable = false)
    var periodId: UUID,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    var amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    var kind: ChargeItemKind,

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation", nullable = false, length = 20)
    var allocation: CostAllocation,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)

@Entity
@Table(name = "unit_invoices")
class UnitInvoiceEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "period_id", nullable = false, updatable = false)
    var periodId: UUID,

    @Column(name = "apartment_id", nullable = false, updatable = false)
    var apartmentId: UUID,

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    var amount: BigDecimal,

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    var paidAmount: BigDecimal,

    @Column(name = "issued_at", nullable = false, updatable = false)
    var issuedAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
