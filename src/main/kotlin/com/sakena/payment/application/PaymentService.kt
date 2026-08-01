package com.sakena.payment.application

import com.sakena.payment.application.command.SubmitPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.payment.domain.model.PaymentId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Application use cases for resident payment submission and manager review. */
@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
) {

    fun submit(command: SubmitPaymentCommand, payerId: UserId): Payment {
        requireRole(payerId, Role.RESIDENT, "Payments can only be submitted by residents")
        val payment = Payment.submit(
            payerId = payerId,
            title = command.title,
            amount = command.amount,
            transactionReference = command.transactionReference,
            receiptObjectKey = command.receiptObjectKey,
        )
        if (paymentRepository.existsByTransactionReference(payment.transactionReference)) {
            throw DomainConflictException(
                "Transaction reference '${payment.transactionReference}' has already been submitted",
            )
        }
        return paymentRepository.save(payment)
    }

    fun confirm(paymentId: PaymentId, managerId: UserId): Payment {
        requireManager(managerId)
        val payment = requirePayment(paymentId)
        payment.confirm(managerId)
        return paymentRepository.save(payment)
    }

    fun reject(paymentId: PaymentId, managerId: UserId, reason: String): Payment {
        requireManager(managerId)
        val payment = requirePayment(paymentId)
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
    fun getPending(managerId: UserId): List<Payment> {
        requireManager(managerId)
        return paymentRepository.findAllPendingNewestFirst()
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
}
