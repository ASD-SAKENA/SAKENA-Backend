package com.sakena.payment.application

import com.sakena.payment.application.command.RecordPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentServiceTest {

    private val repository = mockk<PaymentRepository>()
    private val userRepository = mockk<UserRepository>()
    private val service = PaymentService(repository, userRepository)

    @Test
    fun `record persists a payment for the payer`() {
        val payer = UserId.generate()
        every { userRepository.findById(payer) } returns user(payer, Role.RESIDENT)
        val saved = slot<Payment>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.record(command(), payer)

        assertEquals("Monthly charge", result.title)
        assertEquals(payer, result.payerId)
        assertEquals("TX-123", result.transactionReference)
        assertEquals(PaymentStatus.PENDING, result.status)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `record rejects a payment for an unknown payer`() {
        val payer = UserId.generate()
        every { userRepository.findById(payer) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.record(command(), payer)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `record rejects a payment for a non-resident account`() {
        val payer = UserId.generate()
        every { userRepository.findById(payer) } returns user(payer, Role.STAFF)

        assertFailsWith<DomainValidationException> {
            service.record(command(), payer)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `getHistory returns the payer's payments newest first from the port`() {
        val payer = UserId.generate()
        val newest = confirmedPayment(payer, "Tir charge", "TX-NEW")
        val oldest = confirmedPayment(payer, "Khordad charge", "TX-OLD")
        every { repository.findAllByPayerNewestFirst(payer) } returns listOf(newest, oldest)

        val result = service.getHistory(payer)

        assertEquals(listOf(newest, oldest), result)
    }

    private fun command() = RecordPaymentCommand(
        title = "Monthly charge",
        amount = BigDecimal("850000"),
        transactionReference = "TX-123",
        receiptObjectKey = null,
    )

    private fun confirmedPayment(payer: UserId, title: String, reference: String): Payment =
        Payment.submit(
            payerId = payer,
            title = title,
            amount = BigDecimal("850000"),
            transactionReference = reference,
            receiptObjectKey = null,
        ).also { it.confirm(UserId.generate()) }

    private fun user(id: UserId, role: Role): User {
        val now = Instant.now()
        return User.reconstitute(
            id = id,
            username = "user-${id.value}",
            email = "${id.value}@example.com",
            passwordHash = "hash",
            role = role,
            createdAt = now,
            updatedAt = now,
            active = true,
        )
    }
}
