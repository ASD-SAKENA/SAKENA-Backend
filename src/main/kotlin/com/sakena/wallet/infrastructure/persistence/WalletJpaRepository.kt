package com.sakena.wallet.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletJpaRepository : JpaRepository<WalletEntity, UUID> {
    fun findByBuildingId(buildingId: UUID): WalletEntity?

    fun findByOwnerUserId(ownerUserId: UUID): WalletEntity?
}
