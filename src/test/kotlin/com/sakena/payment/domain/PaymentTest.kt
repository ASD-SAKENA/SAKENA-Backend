package com.sakena.payment.domain

import com.sakena.payment.domain.model.Payment
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentTest {

    private val payer = UserId.generate()

    @Test
    fun `create trims the title and keeps the amount`() {
        val payment = Payment.create(payer, "  Monthly charge  ", BigDecimal("850000"))

        assertEquals("Monthly charge", payment.title)
        assertEquals(BigDecimal("850000"), payment.amount)
        assertEquals(payer, payment.payerId)
    }

    @Test
    fun `create rejects a blank title`() {
        assertFailsWith<DomainValidationException> {
            Payment.create(payer, "   ", BigDecimal.ONE)
        }
    }

    @Test
    fun `create rejects a zero amount`() {
        assertFailsWith<DomainValidationException> {
            Payment.create(payer, "Charge", BigDecimal.ZERO)
        }
    }

    @Test
    fun `create rejects a negative amount`() {
        assertFailsWith<DomainValidationException> {
            Payment.create(payer, "Charge", BigDecimal("-10"))
        }
    }
}
