package com.sakena.wallet.infrastructure.persistence

import com.sakena.wallet.domain.model.WalletOwnerType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** JPA persistence model for wallets. */
@Entity
@Table(name = "wallets")
class WalletEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    var ownerType: WalletOwnerType,

    @Column(name = "owner_user_id", unique = true)
    var ownerUserId: UUID?,

    @Column(name = "balance", nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
