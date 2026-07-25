package com.sakena.payment.application

import com.sakena.payment.application.command.RecordPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class PaymentServiceTest {

    private val repository = mockk<PaymentRepository>()
    private val service = PaymentService(repository)

    @Test
    fun `record persists a payment for the payer`() {
        val payer = UserId.generate()
        val saved = slot<Payment>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.record(RecordPaymentCommand("Monthly charge", BigDecimal("850000")), payer)

        assertEquals("Monthly charge", result.title)
        assertEquals(payer, result.payerId)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `getHistory returns the payer's payments newest first from the port`() {
        val payer = UserId.generate()
        val newest = Payment.create(payer, "Tir charge", BigDecimal("850000"))
        val oldest = Payment.create(payer, "Khordad charge", BigDecimal("800000"))
        every { repository.findAllByPayerNewestFirst(payer) } returns listOf(newest, oldest)

        val result = service.getHistory(payer)

        assertEquals(listOf(newest, oldest), result)
    }
}
