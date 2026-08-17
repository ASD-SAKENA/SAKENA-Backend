package com.sakena.wallet.infrastructure.persistence

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.domain.model.WalletId
import org.springframework.stereotype.Component

/**
 * Adapter implementing the domain [WalletRepository] port on top of Spring
 * Data JPA.
 */
@Component
class WalletRepositoryAdapter(
    private val jpaRepository: WalletJpaRepository,
) : WalletRepository {

    override fun save(wallet: Wallet): Wallet {
        val saved = jpaRepository.save(toEntity(wallet))
        return toDomain(saved)
    }

    override fun findBuildingWallet(buildingId: BuildingId): Wallet? =
        jpaRepository.findByBuildingId(buildingId.value)?.let(::toDomain)

    override fun findByOwner(userId: UserId): Wallet? =
        jpaRepository.findByOwnerUserId(userId.value)?.let(::toDomain)

    private fun toEntity(wallet: Wallet): WalletEntity =
        WalletEntity(
            id = wallet.id.value,
            ownerType = wallet.ownerType,
            ownerUserId = wallet.ownerUserId?.value,
            buildingId = wallet.buildingId?.value,
            balance = wallet.balance,
            createdAt = wallet.createdAt,
            updatedAt = wallet.updatedAt,
        )

    private fun toDomain(entity: WalletEntity): Wallet =
        Wallet.reconstitute(
            id = WalletId(entity.id),
            ownerType = entity.ownerType,
            ownerUserId = entity.ownerUserId?.let(::UserId),
            buildingId = entity.buildingId?.let(::BuildingId),
            balance = entity.balance,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
