package com.sakena.wallet.infrastructure.persistence

import com.sakena.user.domain.UserId
import com.sakena.property.domain.model.BuildingId
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.domain.model.WalletId
import org.springframework.stereotype.Component
import java.util.UUID

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
        jpaRepository.findByOwnerBuildingId(buildingId.value)?.let(::toDomain)

    override fun findOrCreateBuildingWallet(buildingId: BuildingId): Wallet {
        jpaRepository.insertBuildingWalletIfAbsent(UUID.randomUUID(), buildingId.value)
        return jpaRepository.findByOwnerBuildingId(buildingId.value)?.let(::toDomain)
            ?: error("Building wallet provisioning failed for '$buildingId'")
    }

    override fun findByOwner(userId: UserId): Wallet? =
        jpaRepository.findByOwnerUserId(userId.value)?.let(::toDomain)

    private fun toEntity(wallet: Wallet): WalletEntity =
        WalletEntity(
            id = wallet.id.value,
            ownerType = wallet.ownerType,
            ownerUserId = wallet.ownerUserId?.value,
            ownerBuildingId = wallet.ownerBuildingId?.value,
            balance = wallet.balance,
            createdAt = wallet.createdAt,
            updatedAt = wallet.updatedAt,
        )

    private fun toDomain(entity: WalletEntity): Wallet =
        Wallet.reconstitute(
            id = WalletId(entity.id),
            ownerType = entity.ownerType,
            ownerUserId = entity.ownerUserId?.let(::UserId),
            ownerBuildingId = entity.ownerBuildingId?.let(::BuildingId),
            balance = entity.balance,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
