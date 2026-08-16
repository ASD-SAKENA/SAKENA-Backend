package com.sakena.payment.application

import com.sakena.payment.application.command.PaymentReceiptUpload
import com.sakena.payment.application.command.SubmitPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.PaymentReceiptStorage
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentServiceTest {

    private val buildingId = BuildingId.new()
    private val repository = mockk<PaymentRepository>()
    private val userRepository = mockk<UserRepository>()
    private val receiptStorage = mockk<PaymentReceiptStorage>()
    private val buildingAccess = mockk<BuildingAccess> {
        every { residentBuildingId(any()) } returns buildingId
        every { managedBuildingId(any()) } returns buildingId
        every { requireManagerAccess(buildingId, any()) } returns Unit
    }
    private val service = PaymentService(repository, userRepository, receiptStorage, buildingAccess)

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
            service.submit(command(receipt()), resident.id)
        }

        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { receiptStorage.store(any(), any(), any(), any()) }
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
    fun `submit stores a valid optional receipt and keeps only its object key`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { repository.existsByTransactionReference("TX-123") } returns false
        every {
            receiptStorage.store(resident.id, "image/png", 1024, any())
        } returns "payment-receipts/${resident.id}/receipt.png"
        every { repository.save(any()) } answers { firstArg() }

        val result = service.submit(command(receipt()), resident.id)

        assertEquals("payment-receipts/${resident.id}/receipt.png", result.receiptObjectKey)
    }

    @Test
    fun `invalid receipt is rejected before touching object storage`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { repository.existsByTransactionReference("TX-123") } returns false

        assertFailsWith<DomainValidationException> {
            service.submit(command(receipt(contentType = "application/pdf")), resident.id)
        }
        assertFailsWith<DomainValidationException> {
            service.submit(command(receipt(sizeBytes = PaymentService.MAX_RECEIPT_BYTES + 1)), resident.id)
        }
        assertFailsWith<DomainValidationException> {
            service.submit(command(receipt(content = "not an image".toByteArray())), resident.id)
        }

        verify(exactly = 0) { receiptStorage.store(any(), any(), any(), any()) }
    }

    @Test
    fun `failed persistence removes the uploaded receipt`() {
        val resident = user(Role.RESIDENT)
        val objectKey = "payment-receipts/${resident.id}/receipt.png"
        every { userRepository.findById(resident.id) } returns resident
        every { repository.existsByTransactionReference("TX-123") } returns false
        every { receiptStorage.store(any(), any(), any(), any()) } returns objectKey
        every { repository.save(any()) } throws RuntimeException("database unavailable")
        justRun { receiptStorage.delete(objectKey) }

        assertFailsWith<RuntimeException> {
            service.submit(command(receipt()), resident.id)
        }

        verify(exactly = 1) { receiptStorage.delete(objectKey) }
    }

    @Test
    fun `manager and owner may request a temporary receipt link`() {
        val owner = user(Role.RESIDENT)
        val manager = user(Role.MANAGER)
        val payment = pendingPayment(owner.id, "TX-RECEIPT", "payment-receipts/key.png")
        every { repository.findById(payment.id) } returns payment
        every { userRepository.findById(owner.id) } returns owner
        every { userRepository.findById(manager.id) } returns manager
        every { receiptStorage.presignedUrl("payment-receipts/key.png") } returns
            PaymentReceiptAccess("https://signed.example/receipt", 900)

        assertEquals(
            "https://signed.example/receipt",
            service.getReceipt(payment.id, owner.id).url,
        )
        assertEquals(
            "https://signed.example/receipt",
            service.getReceipt(payment.id, manager.id).url,
        )
    }

    @Test
    fun `another resident may not access someone else's receipt`() {
        val owner = user(Role.RESIDENT)
        val otherResident = user(Role.RESIDENT)
        val payment = pendingPayment(owner.id, "TX-PRIVATE", "payment-receipts/private.png")
        every { repository.findById(payment.id) } returns payment
        every { userRepository.findById(otherResident.id) } returns otherResident

        assertFailsWith<DomainForbiddenException> {
            service.getReceipt(payment.id, otherResident.id)
        }

        verify(exactly = 0) { receiptStorage.presignedUrl(any()) }
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
        every { repository.findAllPendingByBuildingNewestFirst(buildingId) } returns pending

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
    fun `manager cannot review another building's payment`() {
        val manager = user(Role.MANAGER)
        val otherBuildingId = BuildingId.new()
        val payment = pendingPayment(UserId.generate(), "TX-OTHER", buildingId = otherBuildingId)
        every { userRepository.findById(manager.id) } returns manager
        every { repository.findById(payment.id) } returns payment
        every { buildingAccess.requireManagerAccess(otherBuildingId, manager.id) } throws
            DomainForbiddenException("You do not manage this building")

        assertFailsWith<DomainForbiddenException> {
            service.confirm(payment.id, manager.id)
        }
        verify(exactly = 0) { repository.save(any()) }
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

    private fun command(receipt: PaymentReceiptUpload? = null) = SubmitPaymentCommand(
        title = "Monthly charge",
        amount = BigDecimal("850000"),
        transactionReference = "TX-123",
        receipt = receipt,
    )

    private fun receipt(
        contentType: String = "image/png",
        sizeBytes: Long = 1024,
        content: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        ),
    ) = PaymentReceiptUpload(
        contentType = contentType,
        sizeBytes = sizeBytes,
        content = content.inputStream(),
    )

    private fun pendingPayment(
        payerId: UserId,
        reference: String,
        receiptObjectKey: String? = null,
        buildingId: BuildingId = this.buildingId,
    ): Payment =
        Payment.submit(
            buildingId = buildingId,
            payerId = payerId,
            title = "Monthly charge",
            amount = BigDecimal("850000"),
            transactionReference = reference,
            receiptObjectKey = receiptObjectKey,
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
