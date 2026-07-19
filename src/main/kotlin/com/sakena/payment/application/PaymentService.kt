package com.sakena.payment.application

import com.sakena.payment.application.command.RecordPaymentCommand
import com.sakena.payment.domain.PaymentRepository
import com.sakena.payment.domain.model.Payment
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service orchestrating the Payment use cases. It owns the
 * transaction boundaries and delegates the business rules to the [Payment]
 * aggregate, depending only on the domain port.
 */
@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun record(command: RecordPaymentCommand, payerId: UserId): Payment {
        val payment = Payment.create(payerId, command.title, command.amount)
        return paymentRepository.save(payment)
    }

    @Transactional(readOnly = true)
    fun getHistory(payerId: UserId): List<Payment> =
        paymentRepository.findAllByPayerNewestFirst(payerId)
}
