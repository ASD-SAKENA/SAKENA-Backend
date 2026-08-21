package com.sakena.payment.application

import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodType
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.payment.application.command.PaymentReceiptUpload
import com.sakena.payment.application.command.SubmitPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.PaymentReceiptStorage
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.wallet.application.WalletService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentServiceTest {

    private val buildingId = BuildingId.new()
    private val apartmentId = ApartmentId.new()
    private val period = ChargePeriod.create(
        buildingId = buildingId,
        title = "Monthly charge",
        type = ChargePeriodType.MONTHLY,
        startsOn = LocalDate.of(2026, 6, 1),
        endsOn = LocalDate.of(2026, 7, 1),
    )
    private val repository = mockk<PaymentRepository>()
    private val userRepository = mockk<UserRepository>()
    private val receiptStorage = mockk<PaymentReceiptStorage>()
    private val buildingAccess = mockk<BuildingAccess> {
        every { managedBuildingId(any()) } returns buildingId
        every { requireManagerAccess(buildingId, any()) } returns Unit
    }
    private val invoiceRepository = mockk<UnitInvoiceRepository>()
    private val periodRepository = mockk<ChargePeriodRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val walletService = mockk<WalletService>()
    private val apartmentRepository = mockk<ApartmentRepository>(relaxed = true)
    private val service = PaymentService(
        repository,
        userRepository,
        receiptStorage,
        buildingAccess,
        invoiceRepository,
        periodRepository,
        residencyRepository,
        walletService,
        apartmentRepository,
    )

    @Test
    fun `submit persists a pending claim for the authenticated resident`() {
        val resident = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        stubSubmitPrerequisites(resident, invoice)
        val saved = slot<Payment>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val result = service.submit(command(invoice.id), resident.id)

        assertEquals(resident.id, result.payerId)
        assertEquals(invoice.id, result.invoiceId)
        assertEquals(period.title, result.title)
        assertEquals("TX-123", result.transactionReference)
        assertEquals(PaymentStatus.PENDING, result.status)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `submit rejects a duplicate transaction reference`() {
        val resident = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        stubSubmitPrerequisites(resident, invoice)
        every { repository.existsByTransactionReference("TX-123") } returns true

        assertFailsWith<DomainConflictException> {
            service.submit(command(invoice.id, receipt()), resident.id)
        }

        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { receiptStorage.store(any(), any(), any(), any()) }
    }

    @Test
    fun `submit rejects an unknown or non-resident account`() {
        val unknown = UserId.generate()
        every { userRepository.findById(unknown) } returns null
        assertFailsWith<EntityNotFoundException> {
            service.submit(command(UnitInvoiceId.new()), unknown)
        }

        val staff = user(Role.STAFF)
        every { userRepository.findById(staff.id) } returns staff
        assertFailsWith<DomainValidationException> {
            service.submit(command(UnitInvoiceId.new()), staff.id)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `submit stores a valid optional receipt and keeps only its object key`() {
        val resident = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        stubSubmitPrerequisites(resident, invoice)
        every {
            receiptStorage.store(resident.id, "image/png", 1024, any())
        } returns "payment-receipts/${resident.id}/receipt.png"
        every { repository.save(any()) } answers { firstArg() }

        val result = service.submit(command(invoice.id, receipt()), resident.id)

        assertEquals("payment-receipts/${resident.id}/receipt.png", result.receiptObjectKey)
    }

    @Test
    fun `submit accepts every supported receipt signature`() {
        val resident = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        stubSubmitPrerequisites(resident, invoice)
        every { receiptStorage.store(resident.id, any(), 1024, any()) } returns
            "payment-receipts/${resident.id}/receipt"
        every { repository.save(any()) } answers { firstArg() }
        val supportedReceipts = listOf(
            receipt(
                contentType = "image/png; charset=binary",
                content = byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                ),
            ),
            receipt(
                contentType = "image/jpeg",
                content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            ),
            receipt(
                contentType = "image/webp",
                content = byteArrayOf(
                    0x52, 0x49, 0x46, 0x46,
                    0x00, 0x00, 0x00, 0x00,
                    0x57, 0x45, 0x42, 0x50,
                ),
            ),
        )

        supportedReceipts.forEach { supported ->
            val result = service.submit(command(invoice.id, supported), resident.id)

            assertEquals("payment-receipts/${resident.id}/receipt", result.receiptObjectKey)
        }
        verify(exactly = 3) { receiptStorage.store(resident.id, any(), 1024, any()) }
    }

    @Test
    fun `invalid receipt is rejected before touching object storage`() {
        val resident = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        stubSubmitPrerequisites(resident, invoice)

        assertFailsWith<DomainValidationException> {
            service.submit(
                command(invoice.id, receipt(sizeBytes = 0, content = byteArrayOf())),
                resident.id,
            )
        }
        assertFailsWith<DomainValidationException> {
            service.submit(command(invoice.id, receipt(contentType = "application/pdf")), resident.id)
        }
        assertFailsWith<DomainValidationException> {
            service.submit(
                command(invoice.id, receipt(sizeBytes = PaymentService.MAX_RECEIPT_BYTES + 1)),
                resident.id,
            )
        }
        assertFailsWith<DomainValidationException> {
            service.submit(
                command(invoice.id, receipt(content = "not an image".toByteArray())),
                resident.id,
            )
        }

        verify(exactly = 0) { receiptStorage.store(any(), any(), any(), any()) }
    }

    @Test
    fun `failed persistence removes the uploaded receipt`() {
        val resident = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        val objectKey = "payment-receipts/${resident.id}/receipt.png"
        stubSubmitPrerequisites(resident, invoice)
        every { receiptStorage.store(any(), any(), any(), any()) } returns objectKey
        every { repository.save(any()) } throws RuntimeException("database unavailable")
        justRun { receiptStorage.delete(objectKey) }

        assertFailsWith<RuntimeException> {
            service.submit(command(invoice.id, receipt()), resident.id)
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
        val payer = user(Role.RESIDENT)
        val pending = listOf(pendingPayment(payer.id, "TX-PENDING"))
        every { userRepository.findById(manager.id) } returns manager
        every { userRepository.findById(payer.id) } returns payer
        every { repository.findAllPendingByBuildingNewestFirst(buildingId) } returns pending
        every { invoiceRepository.findById(any()) } returns outstandingInvoice()
        every { periodRepository.findById(period.id) } returns period

        val result = service.getPending(manager.id)
        assertEquals(1, result.size)
        assertEquals(pending[0].id, result[0].payment.id)
        assertEquals(period.title, result[0].periodTitle)
        assertEquals(payer.username, result[0].payerUsername)
    }

    @Test
    fun `getBuildingPayments filters by status and period`() {
        val manager = user(Role.MANAGER)
        val payer = user(Role.RESIDENT)
        val invoice = outstandingInvoice()
        val confirmed = pendingPayment(payer.id, "TX-OK", invoiceId = invoice.id).also {
            it.confirm(manager.id)
        }
        val other = pendingPayment(payer.id, "TX-WAIT", invoiceId = invoice.id)
        every { userRepository.findById(manager.id) } returns manager
        every { userRepository.findById(payer.id) } returns payer
        every { repository.findAllByBuildingNewestFirst(buildingId) } returns listOf(confirmed, other)
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { periodRepository.findById(period.id) } returns period

        val confirmedOnly = service.getBuildingPayments(
            manager.id,
            BuildingPaymentQuery(status = PaymentStatus.CONFIRMED),
        )
        assertEquals(listOf(confirmed.id), confirmedOnly.map { it.payment.id })

        val byPeriod = service.getBuildingPayments(
            manager.id,
            BuildingPaymentQuery(periodId = period.id),
        )
        assertEquals(2, byPeriod.size)
    }

    @Test
    fun `confirm reviews invoice payment and credits the building wallet`() {
        val manager = user(Role.MANAGER)
        val invoice = outstandingInvoice()
        val payment = pendingPayment(UserId.generate(), "TX-CONFIRM", invoiceId = invoice.id)
        every { userRepository.findById(manager.id) } returns manager
        every { repository.findById(payment.id) } returns payment
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { invoiceRepository.save(invoice) } returns invoice
        justRun {
            walletService.recordChargeCollection(
                buildingId = buildingId,
                amount = payment.amount,
                description = "وصول «${payment.title}»",
            )
        }
        every { repository.save(payment) } returns payment

        val result = service.confirm(payment.id, manager.id)

        assertEquals(PaymentStatus.CONFIRMED, result.status)
        assertEquals(manager.id, result.reviewedBy)
        assertEquals(payment.amount, invoice.paidAmount)
        verify(exactly = 1) { invoiceRepository.save(invoice) }
        verify(exactly = 1) {
            walletService.recordChargeCollection(
                buildingId = buildingId,
                amount = payment.amount,
                description = "وصول «${payment.title}»",
            )
        }
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
        verify(exactly = 0) { walletService.recordChargeCollection(any(), any(), any()) }
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

    private fun stubSubmitPrerequisites(resident: User, invoice: UnitInvoice) {
        every { userRepository.findById(resident.id) } returns resident
        every { invoiceRepository.findById(invoice.id) } returns invoice
        every { residencyRepository.findActiveByResident(resident.id) } returns
            Residency.start(apartmentId, resident.id, TenancyType.TENANT)
        every { periodRepository.findById(period.id) } returns period
        every { repository.existsPendingForInvoice(invoice.id) } returns false
        every { repository.existsByTransactionReference("TX-123") } returns false
    }

    private fun outstandingInvoice(amount: BigDecimal = BigDecimal("850000")): UnitInvoice =
        UnitInvoice.issue(period.id, apartmentId, amount)

    private fun command(
        invoiceId: UnitInvoiceId,
        receipt: PaymentReceiptUpload? = null,
        amount: BigDecimal = BigDecimal("850000"),
    ) = SubmitPaymentCommand(
        invoiceId = invoiceId,
        amount = amount,
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
        invoiceId: UnitInvoiceId = UnitInvoiceId.new(),
    ): Payment =
        Payment.submit(
            buildingId = buildingId,
            invoiceId = invoiceId,
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
            managedBuildingId = if (role == Role.MANAGER) BuildingId.new() else null,
        )
    }
}
