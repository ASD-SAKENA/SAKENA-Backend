package com.sakena.wallet.infrastructure.persistence

import com.sakena.wallet.domain.model.WalletOwnerType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletJpaRepository : JpaRepository<WalletEntity, UUID> {
    fun findFirstByOwnerType(ownerType: WalletOwnerType): WalletEntity?

    fun findByOwnerUserId(ownerUserId: UUID): WalletEntity?
}
