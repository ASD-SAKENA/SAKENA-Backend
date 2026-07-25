package com.sakena.wallet.domain.model

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Value object identifying a [Wallet]. */
@JvmInline
value class WalletId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): WalletId = WalletId(UUID.randomUUID())
    }
}

enum class WalletOwnerType {
    /** The building's shared account that wages are paid from. */
    BUILDING,

    /** A personal wallet (e.g. a service worker receiving wages). */
    USER,
}

/**
 * Wallet aggregate root — holds a balance for either the building account or
 * an individual user. The building account may run a deficit (buildings can
 * owe wages); personal wallets can never go negative.
 */
class Wallet private constructor(
    val id: WalletId,
    val ownerType: WalletOwnerType,
    val ownerUserId: UserId?,
    balance: BigDecimal,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var balance: BigDecimal = balance
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun credit(amount: BigDecimal) {
        validateAmount(amount)
        balance += amount
        touch()
    }

    fun debit(amount: BigDecimal) {
        validateAmount(amount)
        if (ownerType == WalletOwnerType.USER && balance < amount) {
            throw DomainConflictException("Insufficient wallet balance")
        }
        balance -= amount
        touch()
    }

    private fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        fun createBuilding(): Wallet = create(WalletOwnerType.BUILDING, null)

        fun createForUser(userId: UserId): Wallet = create(WalletOwnerType.USER, userId)

        private fun create(ownerType: WalletOwnerType, ownerUserId: UserId?): Wallet {
            val now = Instant.now()
            return Wallet(
                id = WalletId.new(),
                ownerType = ownerType,
                ownerUserId = ownerUserId,
                balance = BigDecimal.ZERO,
                createdAt = now,
                updatedAt = now,
            )
        }

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: WalletId,
            ownerType: WalletOwnerType,
            ownerUserId: UserId?,
            balance: BigDecimal,
            createdAt: Instant,
            updatedAt: Instant,
        ): Wallet = Wallet(id, ownerType, ownerUserId, balance, createdAt, updatedAt)

        private fun validateAmount(amount: BigDecimal) {
            if (amount <= BigDecimal.ZERO) {
                throw DomainValidationException("Wallet amount must be greater than zero")
            }
        }
    }
}
