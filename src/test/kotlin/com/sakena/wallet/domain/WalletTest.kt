package com.sakena.wallet.domain

import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.model.Wallet
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletTest {

    @Test
    fun `credit increases the balance`() {
        val wallet = Wallet.createForUser(UserId.generate())

        wallet.credit(BigDecimal("500000"))

        assertEquals(BigDecimal("500000"), wallet.balance)
    }

    @Test
    fun `a user wallet cannot be overdrawn`() {
        val wallet = Wallet.createForUser(UserId.generate())
        wallet.credit(BigDecimal("100"))

        assertFailsWith<DomainConflictException> {
            wallet.debit(BigDecimal("200"))
        }
    }

    @Test
    fun `the building wallet may run a deficit`() {
        val wallet = Wallet.createBuilding()

        wallet.debit(BigDecimal("300000"))

        assertEquals(BigDecimal("-300000"), wallet.balance)
    }

    @Test
    fun `non-positive amounts are rejected`() {
        val wallet = Wallet.createBuilding()

        assertFailsWith<DomainValidationException> { wallet.credit(BigDecimal.ZERO) }
        assertFailsWith<DomainValidationException> { wallet.debit(BigDecimal("-1")) }
    }
}
