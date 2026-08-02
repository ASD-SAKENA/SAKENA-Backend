package com.sakena.payment.domain

import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentTest {

    private val payer = UserId.generate()
    private val manager = UserId.generate()

    @Test
    fun `submit creates a pending payment claim with normalized evidence`() {
        val payment = submit(
            title = "  Monthly charge  ",
            reference = "  TX-123  ",
            receiptObjectKey = "  payment-receipts/TX-123.jpg  ",
        )

        assertEquals("Monthly charge", payment.title)
        assertEquals(BigDecimal("850000"), payment.amount)
        assertEquals("TX-123", payment.transactionReference)
        assertEquals("payment-receipts/TX-123.jpg", payment.receiptObjectKey)
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertNull(payment.reviewedBy)
        assertNull(payment.reviewedAt)
    }

    @Test
    fun `confirm moves a pending payment into permanent history`() {
        val payment = submit()

        payment.confirm(manager)

        assertEquals(PaymentStatus.CONFIRMED, payment.status)
        assertEquals(manager, payment.reviewedBy)
        assertNotNull(payment.reviewedAt)
        assertNull(payment.rejectionReason)
    }

    @Test
    fun `attach receipt stores a normalized object key on a pending payment`() {
        val payment = submit()

        payment.attachReceipt("  payment-receipts/receipt.png  ")

        assertEquals("payment-receipts/receipt.png", payment.receiptObjectKey)
    }

    @Test
    fun `receipt cannot be replaced or attached after review`() {
        val paymentWithReceipt = submit(receiptObjectKey = "payment-receipts/original.png")
        assertFailsWith<DomainConflictException> {
            paymentWithReceipt.attachReceipt("payment-receipts/replacement.png")
        }

        val confirmedPayment = submit().also { it.confirm(manager) }
        assertFailsWith<DomainConflictException> {
            confirmedPayment.attachReceipt("payment-receipts/late.png")
        }
    }

    @Test
    fun `reject records the manager and normalized reason`() {
        val payment = submit()

        payment.reject(manager, "  Reference could not be verified  ")

        assertEquals(PaymentStatus.REJECTED, payment.status)
        assertEquals(manager, payment.reviewedBy)
        assertNotNull(payment.reviewedAt)
        assertEquals("Reference could not be verified", payment.rejectionReason)
    }

    @Test
    fun `a reviewed payment cannot be reviewed again`() {
        val payment = submit()
        payment.confirm(manager)

        assertFailsWith<DomainConflictException> {
            payment.reject(manager, "Duplicate")
        }
    }

    @Test
    fun `blank rejection reason leaves payment pending`() {
        val payment = submit()

        assertFailsWith<DomainValidationException> {
            payment.reject(manager, "   ")
        }

        assertEquals(PaymentStatus.PENDING, payment.status)
        assertNull(payment.reviewedBy)
    }

    @Test
    fun `submit rejects a blank title`() {
        assertFailsWith<DomainValidationException> {
            submit(title = "   ")
        }
    }

    @Test
    fun `submit rejects a non-positive amount`() {
        assertFailsWith<DomainValidationException> {
            submit(amount = BigDecimal.ZERO)
        }
        assertFailsWith<DomainValidationException> {
            submit(amount = BigDecimal("-10"))
        }
    }

    @Test
    fun `submit rejects a blank transaction reference`() {
        assertFailsWith<DomainValidationException> {
            submit(reference = "   ")
        }
    }

    private fun submit(
        title: String = "Monthly charge",
        amount: BigDecimal = BigDecimal("850000"),
        reference: String = "TX-123",
        receiptObjectKey: String? = null,
    ): Payment = Payment.submit(
        payerId = payer,
        title = title,
        amount = amount,
        transactionReference = reference,
        receiptObjectKey = receiptObjectKey,
    )
}
