package com.sakena.wallet.application

import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.UserId
import com.sakena.wallet.application.command.RecordBuildingTransactionCommand
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.WalletTransactionRepository
import com.sakena.wallet.domain.model.TransactionCategory
import com.sakena.wallet.domain.model.TransactionDirection
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.domain.model.WalletTransaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Application service for wallet use cases. Every balance change is written to
 * the ledger in the same transaction, so the building's statement always
 * explains its current balance.
 */
@Service
@Transactional
class WalletService(
    private val walletRepository: WalletRepository,
    private val transactionRepository: WalletTransactionRepository,
    private val serviceRequestRepository: ServiceRequestRepository,
) {

    fun settleServiceRequest(serviceRequestId: ServiceRequestId, settledBy: UserId) {
        val request = serviceRequestRepository.findById(serviceRequestId)
            ?: throw EntityNotFoundException("Service request with id '$serviceRequestId' was not found")

        val settled = request.settle(settledBy)
        val amount = BigDecimal.valueOf(
            settled.completionCost
                ?: throw DomainConflictException("Service request has no completion cost"),
        )
        val worker = settled.assignedTo
            ?: throw DomainConflictException("Service request has no assigned worker")

        val buildingWallet = requireBuildingWallet()
        val workerWallet = walletRepository.findByOwner(worker) ?: Wallet.createForUser(worker)

        buildingWallet.debit(amount)
        workerWallet.credit(amount)

        walletRepository.save(buildingWallet)
        walletRepository.save(workerWallet)
        serviceRequestRepository.save(settled)

        val label = "دستمزد «${settled.title}»"
        recordTransaction(
            buildingWallet,
            TransactionDirection.DEBIT,
            TransactionCategory.WAGE_SETTLEMENT,
            amount,
            label,
        )
        recordTransaction(
            workerWallet,
            TransactionDirection.CREDIT,
            TransactionCategory.WAGE_SETTLEMENT,
            amount,
            label,
        )
    }

    /** Manager-driven credit or debit of the shared building account. */
    fun recordBuildingTransaction(command: RecordBuildingTransactionCommand): Wallet {
        val wallet = requireBuildingWallet()
        when (command.direction) {
            TransactionDirection.CREDIT -> wallet.credit(command.amount)
            TransactionDirection.DEBIT -> wallet.debit(command.amount)
        }
        val saved = walletRepository.save(wallet)
        recordTransaction(
            saved,
            command.direction,
            command.category,
            command.amount,
            command.description,
        )
        return saved
    }

    @Transactional(readOnly = true)
    fun getBuildingWallet(): Wallet = requireBuildingWallet()

    @Transactional(readOnly = true)
    fun getBuildingLedger(): List<WalletTransaction> =
        transactionRepository.findAllByWalletNewestFirst(requireBuildingWallet().id)

    @Transactional(readOnly = true)
    fun getMyWallet(userId: UserId): Wallet =
        walletRepository.findByOwner(userId) ?: Wallet.createForUser(userId)

    @Transactional(readOnly = true)
    fun getMyLedger(userId: UserId): List<WalletTransaction> {
        val wallet = walletRepository.findByOwner(userId) ?: return emptyList()
        return transactionRepository.findAllByWalletNewestFirst(wallet.id)
    }

    private fun recordTransaction(
        wallet: Wallet,
        direction: TransactionDirection,
        category: TransactionCategory,
        amount: BigDecimal,
        description: String,
    ) {
        transactionRepository.save(
            WalletTransaction.record(
                walletId = wallet.id,
                direction = direction,
                category = category,
                amount = amount,
                description = description,
                balanceAfter = wallet.balance,
            ),
        )
    }

    private fun requireBuildingWallet(): Wallet =
        walletRepository.findBuildingWallet()
            ?: throw EntityNotFoundException("Building wallet was not found")
}
