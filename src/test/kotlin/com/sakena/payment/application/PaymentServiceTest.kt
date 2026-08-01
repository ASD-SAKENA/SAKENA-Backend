package com.sakena.payment.application

import com.sakena.payment.application.command.SubmitPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.shared.domain.DomainConflictException
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
    fun `submit persists a pending claim for the authenticated resident`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { repository.existsByTransactionReference("TX-123") } returns false
        val saved = slot<Payment>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.submit(command(), resident.id)

        assertEquals(resident.id, result.payerId)
        assertEquals("TX-123", result.transactionReference)
        assertEquals(PaymentStatus.PENDING, result.status)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `submit rejects a duplicate transaction reference`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { repository.existsByTransactionReference("TX-123") } returns true

        assertFailsWith<DomainConflictException> {
            service.submit(command(), resident.id)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `submit rejects an unknown or non-resident account`() {
        val unknown = UserId.generate()
        every { userRepository.findById(unknown) } returns null
        assertFailsWith<EntityNotFoundException> { service.submit(command(), unknown) }

        val staff = user(Role.STAFF)
        every { userRepository.findById(staff.id) } returns staff
        assertFailsWith<DomainValidationException> { service.submit(command(), staff.id) }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `getSubmissions returns every status for only that resident`() {
        val residentId = UserId.generate()
        val submissions = listOf(pendingPayment(residentId, "TX-PENDING"))
        every { repository.findAllSubmissionsByPayerNewestFirst(residentId) } returns submissions

        assertEquals(submissions, service.getSubmissions(residentId))
    }

    @Test
    fun `getHistory returns only confirmed payments from the repository`() {
        val residentId = UserId.generate()
        val confirmed = pendingPayment(residentId, "TX-CONFIRMED").also {
            it.confirm(UserId.generate())
        }
        every { repository.findAllByPayerNewestFirst(residentId) } returns listOf(confirmed)

        assertEquals(listOf(confirmed), service.getHistory(residentId))
    }

    @Test
    fun `getPending returns the manager review queue newest first`() {
        val manager = user(Role.MANAGER)
        val pending = listOf(pendingPayment(UserId.generate(), "TX-PENDING"))
        every { userRepository.findById(manager.id) } returns manager
        every { repository.findAllPendingNewestFirst() } returns pending

        assertEquals(pending, service.getPending(manager.id))
    }

    @Test
    fun `confirm reviews and saves a pending payment as manager`() {
        val manager = user(Role.MANAGER)
        val payment = pendingPayment(UserId.generate(), "TX-CONFIRM")
        every { userRepository.findById(manager.id) } returns manager
        every { repository.findById(payment.id) } returns payment
        every { repository.save(payment) } returns payment

        val result = service.confirm(payment.id, manager.id)

        assertEquals(PaymentStatus.CONFIRMED, result.status)
        assertEquals(manager.id, result.reviewedBy)
        verify(exactly = 1) { repository.save(payment) }
    }

    @Test
    fun `reject reviews and saves a pending payment with a reason`() {
        val manager = user(Role.MANAGER)
        val payment = pendingPayment(UserId.generate(), "TX-REJECT")
        every { userRepository.findById(manager.id) } returns manager
        every { repository.findById(payment.id) } returns payment
        every { repository.save(payment) } returns payment

        val result = service.reject(payment.id, manager.id, "Reference not found")

        assertEquals(PaymentStatus.REJECTED, result.status)
        assertEquals("Reference not found", result.rejectionReason)
    }

    @Test
    fun `review rejects a non-manager account`() {
        val staff = user(Role.STAFF)
        every { userRepository.findById(staff.id) } returns staff

        assertFailsWith<DomainValidationException> {
            service.confirm(pendingPayment(UserId.generate(), "TX-1").id, staff.id)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `review rejects an unknown payment`() {
        val manager = user(Role.MANAGER)
        val paymentId = com.sakena.payment.domain.model.PaymentId.new()
        every { userRepository.findById(manager.id) } returns manager
        every { repository.findById(paymentId) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.confirm(paymentId, manager.id)
        }
    }

    private fun command() = SubmitPaymentCommand(
        title = "Monthly charge",
        amount = BigDecimal("850000"),
        transactionReference = "TX-123",
        receiptObjectKey = null,
    )

    private fun pendingPayment(payerId: UserId, reference: String): Payment =
        Payment.submit(
            payerId = payerId,
            title = "Monthly charge",
            amount = BigDecimal("850000"),
            transactionReference = reference,
            receiptObjectKey = null,
        )

    private fun user(role: Role): User {
        val id = UserId.generate()
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
