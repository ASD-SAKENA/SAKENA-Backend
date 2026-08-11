package com.sakena.wallet.infrastructure.persistence

import com.sakena.wallet.domain.WalletTransactionRepository
import com.sakena.wallet.domain.model.WalletId
import com.sakena.wallet.domain.model.WalletTransaction
import com.sakena.wallet.domain.model.WalletTransactionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface WalletTransactionJpaRepository : JpaRepository<WalletTransactionEntity, UUID> {
    fun findAllByWalletIdOrderByOccurredAtDesc(walletId: UUID): List<WalletTransactionEntity>
}

/**
 * Adapter implementing the domain [WalletTransactionRepository] port on top of
 * Spring Data JPA.
 */
@Component
class WalletTransactionRepositoryAdapter(
    private val jpaRepository: WalletTransactionJpaRepository,
) : WalletTransactionRepository {

    override fun save(transaction: WalletTransaction): WalletTransaction {
        jpaRepository.save(
            WalletTransactionEntity(
                id = transaction.id.value,
                walletId = transaction.walletId.value,
                direction = transaction.direction,
                category = transaction.category,
                amount = transaction.amount,
                description = transaction.description,
                balanceAfter = transaction.balanceAfter,
                occurredAt = transaction.occurredAt,
            ),
        )
        return transaction
    }

    override fun findAllByWalletNewestFirst(walletId: WalletId): List<WalletTransaction> =
        jpaRepository.findAllByWalletIdOrderByOccurredAtDesc(walletId.value).map { entity ->
            WalletTransaction.reconstitute(
                id = WalletTransactionId(entity.id),
                walletId = WalletId(entity.walletId),
                direction = entity.direction,
                category = entity.category,
                amount = entity.amount,
                description = entity.description,
                balanceAfter = entity.balanceAfter,
                occurredAt = entity.occurredAt,
            )
        }
}
