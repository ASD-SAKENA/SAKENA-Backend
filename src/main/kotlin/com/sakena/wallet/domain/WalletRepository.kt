package com.sakena.wallet.domain

import com.sakena.user.domain.UserId
import com.sakena.property.domain.model.BuildingId
import com.sakena.wallet.domain.model.Wallet

/**
 * Outbound port for persisting wallets. Declared in the domain layer and
 * implemented by an adapter in infrastructure.
 */
interface WalletRepository {
    fun save(wallet: Wallet): Wallet

    /** The shared account belonging to one building. */
    fun findBuildingWallet(buildingId: BuildingId): Wallet?

    /** Atomically provisions the building wallet when it does not exist yet. */
    fun findOrCreateBuildingWallet(buildingId: BuildingId): Wallet

    fun findByOwner(userId: UserId): Wallet?
}
