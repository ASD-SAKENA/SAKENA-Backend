package com.sakena.payment.application

import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceNotFoundException
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.payment.application.command.PaymentReceiptUpload
import com.sakena.payment.application.command.SubmitPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.PaymentReceiptAccess
import com.sakena.payment.domain.PaymentReceiptStorage
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentId
import com.sakena.payment.domain.model.PaymentStatus
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.wallet.application.WalletService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedInputStream
import java.io.InputStream

/** Application use cases for resident payment submission and manager review. */
@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val receiptStorage: PaymentReceiptStorage,
    private val buildingAccess: BuildingAccess,
    private val invoiceRepository: UnitInvoiceRepository,
    private val periodRepository: ChargePeriodRepository,
    private val residencyRepository: ResidencyRepository,
    private val walletService: WalletService,
    private val apartmentRepository: ApartmentRepository,
) {

    companion object {
        const val MAX_RECEIPT_BYTES = 5L * 1024 * 1024
        private val ALLOWED_RECEIPT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }

    fun submit(command: SubmitPaymentCommand, payerId: UserId): Payment {
        requireRole(payerId, Role.RESIDENT, "Payments can only be submitted by residents")
        val invoice = requireOwnOutstandingInvoice(command.invoiceId, payerId)
        val period = periodRepository.findById(invoice.periodId)
            ?: throw EntityNotFoundException("Charge period for invoice '${invoice.id}' was not found")
        if (command.amount > invoice.remaining) {
            throw DomainConflictException("Payment exceeds the outstanding amount of this invoice")
        }
        if (paymentRepository.existsPendingForInvoice(invoice.id)) {
            throw DomainConflictException(
                "This invoice already has a payment waiting for manager review",
            )
        }
        if (paymentRepository.existsByTransactionReference(command.transactionReference.trim())) {
            throw DomainConflictException(
                "Transaction reference '${command.transactionReference.trim()}' has already been submitted",
            )
        }

        val payment = Payment.submit(
            buildingId = period.buildingId,
            invoiceId = invoice.id,
            payerId = payerId,
            title = period.title,
            amount = command.amount,
            transactionReference = command.transactionReference,
            receiptObjectKey = null,
        )
        val objectKey = command.receipt?.let { receipt ->
            val validatedContent = validateReceipt(receipt)
            receiptStorage.store(
                payerId = payerId,
                contentType = receipt.contentType,
                sizeBytes = receipt.sizeBytes,
                content = validatedContent,
            ).also(payment::attachReceipt)
        }
        return try {
            paymentRepository.save(payment)
        } catch (exception: Exception) {
            objectKey?.let { runCatching { receiptStorage.delete(it) } }
            throw exception
        }
    }

    fun confirm(paymentId: PaymentId, managerId: UserId): Payment {
        requireManager(managerId)
        val payment = requirePayment(paymentId)
        requireManagerAccess(payment, managerId)
        val invoiceId = payment.invoiceId
            ?: throw DomainConflictException("This legacy payment is not linked to an invoice")
        val invoice = invoiceRepository.findById(invoiceId)
            ?: throw UnitInvoiceNotFoundException(invoiceId)
        invoice.registerPayment(payment.amount)
        invoiceRepository.save(invoice)
        walletService.recordChargeCollection(
            buildingId = payment.buildingId
                ?: throw DomainConflictException("This payment is not assigned to a building"),
            amount = payment.amount,
            description = "وصول «${payment.title}»",
        )
        payment.confirm(managerId)
        return paymentRepository.save(payment)
    }

    fun reject(paymentId: PaymentId, managerId: UserId, reason: String): Payment {
        requireManager(managerId)
        val payment = requirePayment(paymentId)
        requireManagerAccess(payment, managerId)
        payment.reject(managerId, reason)
        return paymentRepository.save(payment)
    }

    @Transactional(readOnly = true)
    fun getHistory(payerId: UserId): List<Payment> =
        paymentRepository.findAllByPayerNewestFirst(payerId)

    @Transactional(readOnly = true)
    fun getSubmissions(payerId: UserId): List<Payment> =
        paymentRepository.findAllSubmissionsByPayerNewestFirst(payerId)

    @Transactional(readOnly = true)
    fun getPending(managerId: UserId): List<PaymentDetails> {
        requireManager(managerId)
        return paymentRepository.findAllPendingByBuildingNewestFirst(
            buildingAccess.managedBuildingId(managerId),
        ).map(::detailsOf)
    }

    /**
     * Full payment ledger for the manager's building — every status, optionally
     * narrowed to one charge period or review status.
     */
    @Transactional(readOnly = true)
    fun getBuildingPayments(managerId: UserId, query: BuildingPaymentQuery): List<PaymentDetails> {
        requireManager(managerId)
        val buildingId = buildingAccess.managedBuildingId(managerId)
        query.periodId?.let { periodId ->
            val period = periodRepository.findById(periodId)
                ?: throw EntityNotFoundException("Charge period with id '$periodId' was not found")
            buildingAccess.requireManagerAccess(period.buildingId, managerId)
        }
        return paymentRepository.findAllByBuildingNewestFirst(buildingId)
            .asSequence()
            .filter { payment -> query.status == null || payment.status == query.status }
            .map(::detailsOf)
            .filter { details ->
                query.periodId == null || details.periodId == query.periodId
            }
            .toList()
    }

    @Transactional(readOnly = true)
    fun periodTitleOf(payment: Payment): String? =
        detailsOf(payment).periodTitle

    @Transactional(readOnly = true)
    fun detailsOf(payment: Payment): PaymentDetails {
        val invoice = payment.invoiceId?.let(invoiceRepository::findById)
        val period = invoice?.periodId?.let(periodRepository::findById)
        val unitNumber = invoice?.apartmentId
            ?.let(apartmentRepository::findById)
            ?.unitNumber
        val payerUsername = userRepository.findById(payment.payerId)?.username
        return PaymentDetails(
            payment = payment,
            periodId = period?.id ?: invoice?.periodId,
            periodTitle = period?.title ?: payment.title.takeIf { it.isNotBlank() },
            unitNumber = unitNumber,
            payerUsername = payerUsername,
        )
    }

    @Transactional(readOnly = true)
    fun getReceipt(paymentId: PaymentId, requesterId: UserId): PaymentReceiptAccess {
        val payment = requirePayment(paymentId)
        val requester = userRepository.findById(requesterId)
            ?: throw EntityNotFoundException("User with id '$requesterId' was not found")
        if (requester.role == Role.MANAGER) {
            requireManagerAccess(payment, requesterId)
        } else if (payment.payerId != requesterId) {
            throw DomainForbiddenException("You may not access this payment receipt")
        }
        val objectKey = payment.receiptObjectKey
            ?: throw EntityNotFoundException("Payment with id '$paymentId' has no receipt")
        return receiptStorage.presignedUrl(objectKey)
    }

    private fun requireOwnOutstandingInvoice(
        invoiceId: UnitInvoiceId,
        residentId: UserId,
    ): UnitInvoice {
        val invoice = invoiceRepository.findById(invoiceId)
            ?: throw UnitInvoiceNotFoundException(invoiceId)
        val residency = residencyRepository.findActiveByResident(residentId)
            ?: throw DomainForbiddenException("You must be an active resident to pay an invoice")
        if (residency.apartmentId != invoice.apartmentId) {
            throw DomainForbiddenException("You may only pay invoices for your own apartment")
        }
        if (invoice.remaining <= java.math.BigDecimal.ZERO) {
            throw DomainConflictException("Invoice is already fully paid")
        }
        return invoice
    }

    private fun requireManager(userId: UserId) =
        requireRole(userId, Role.MANAGER, "Only managers can review payments")

    private fun requireRole(userId: UserId, role: Role, message: String) {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User with id '$userId' was not found")
        if (user.role != role) throw DomainValidationException(message)
    }

    private fun requirePayment(paymentId: PaymentId): Payment =
        paymentRepository.findById(paymentId)
            ?: throw EntityNotFoundException("Payment with id '$paymentId' was not found")

    private fun requireManagerAccess(payment: Payment, managerId: UserId) {
        val buildingId = payment.buildingId
            ?: throw DomainForbiddenException("This legacy payment is not assigned to a building")
        buildingAccess.requireManagerAccess(buildingId, managerId)
    }

    private fun validateReceipt(receipt: PaymentReceiptUpload): InputStream {
        if (receipt.sizeBytes <= 0) throw DomainValidationException("Receipt file must not be empty")
        if (receipt.sizeBytes > MAX_RECEIPT_BYTES) {
            throw DomainValidationException("Receipt file must be at most 5 MB")
        }
        val baseType = receipt.contentType.substringBefore(';').trim().lowercase()
        if (baseType !in ALLOWED_RECEIPT_TYPES) {
            throw DomainValidationException("Unsupported receipt type '$baseType'")
        }
        val content = BufferedInputStream(receipt.content)
        content.mark(12)
        val signature = content.readNBytes(12)
        content.reset()
        val signatureMatches = when (baseType) {
            "image/png" -> signature.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            "image/jpeg" -> signature.startsWith(0xFF, 0xD8, 0xFF)
            "image/webp" ->
                signature.startsWith(0x52, 0x49, 0x46, 0x46) &&
                    signature.sliceArray(8 until minOf(12, signature.size))
                        .startsWith(0x57, 0x45, 0x42, 0x50)
            else -> false
        }
        if (!signatureMatches) {
            throw DomainValidationException("Receipt content does not match type '$baseType'")
        }
        return content
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all {
            (this[it].toInt() and 0xFF) == expected[it]
        }
}
