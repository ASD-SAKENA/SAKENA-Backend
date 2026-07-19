package com.sakena.wallet.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.model.Wallet
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Application service for wallet use cases. Settlement is the one
 * cross-context transaction: it marks the completed service request as
 * settled, debits the building account and credits the worker's wallet —
 * all in a single transaction boundary.
 */
@Service
@Transactional
class WalletService(
    private val walletRepository: WalletRepository,
    private val serviceRequestRepository: ServiceRequestRepository,
) {

    fun settleServiceRequest(serviceRequestId: ServiceRequestId, settledBy: UserId) {
        val request = serviceRequestRepository.findById(serviceRequestId)
            ?: throw EntityNotFoundException("Service request with id '$serviceRequestId' was not found")

        val settled = request.settle(settledBy)
        val amount = BigDecimal.valueOf(
            settled.completionCost
                ?: throw DomainConflictException("Service request has no completion cost")
        )
        val worker = settled.assignedTo
            ?: throw DomainConflictException("Service request has no assigned worker")

        val buildingWallet = walletRepository.findBuildingWallet()
            ?: throw EntityNotFoundException("Building wallet was not found")
        val workerWallet = walletRepository.findByOwner(worker)
            ?: Wallet.createForUser(worker)

        buildingWallet.debit(amount)
        workerWallet.credit(amount)

        walletRepository.save(buildingWallet)
        walletRepository.save(workerWallet)
        serviceRequestRepository.save(settled)
    }

    @Transactional(readOnly = true)
    fun getMyWallet(userId: UserId): Wallet =
        walletRepository.findByOwner(userId) ?: Wallet.createForUser(userId)
}
