package com.sakena.wallet.infrastructure.persistence

import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.domain.model.WalletId
import com.sakena.wallet.domain.model.WalletOwnerType
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

    override fun findBuildingWallet(): Wallet? =
        jpaRepository.findFirstByOwnerType(WalletOwnerType.BUILDING)?.let(::toDomain)

    override fun findByOwner(userId: UserId): Wallet? =
        jpaRepository.findByOwnerUserId(userId.value)?.let(::toDomain)

    private fun toEntity(wallet: Wallet): WalletEntity =
        WalletEntity(
            id = wallet.id.value,
            ownerType = wallet.ownerType,
            ownerUserId = wallet.ownerUserId?.value,
            balance = wallet.balance,
            createdAt = wallet.createdAt,
            updatedAt = wallet.updatedAt,
        )

    private fun toDomain(entity: WalletEntity): Wallet =
        Wallet.reconstitute(
            id = WalletId(entity.id),
            ownerType = entity.ownerType,
            ownerUserId = entity.ownerUserId?.let(::UserId),
            balance = entity.balance,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
