package com.sakena.wallet.application

import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.property.domain.ApartmentRepository
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequest
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
    private val apartmentRepository: ApartmentRepository,
    private val serviceChargeRepository: ServiceChargeRepository,
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
        val serviceCharge = when (settled.costResponsibility) {
            ServiceCostResponsibility.BUILDING_WALLET -> null
            ServiceCostResponsibility.ALL_UNITS -> createServiceCharge(
                settled,
                amount,
                ServiceChargeTarget.ALL_UNITS,
            )
            ServiceCostResponsibility.REQUESTING_UNIT -> createServiceCharge(
                settled,
                amount,
                ServiceChargeTarget.SPECIFIC_UNIT,
            )
            null -> throw DomainConflictException("Service request cost responsibility has not been assigned")
        }

        val buildingWallet = requireBuildingWallet()
        val workerWallet = walletRepository.findByOwner(worker) ?: Wallet.createForUser(worker)

        buildingWallet.debit(amount)
        workerWallet.credit(amount)

        serviceCharge?.let(serviceChargeRepository::save)
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

    private fun createServiceCharge(
        request: ServiceRequest,
        amount: BigDecimal,
        target: ServiceChargeTarget,
    ): ServiceCharge {
        val apartmentId = request.requestingApartmentId
            ?: throw DomainConflictException("Service request has no requesting apartment for billing")
        val apartment = apartmentRepository.findById(apartmentId)
            ?: throw EntityNotFoundException("Requesting apartment with id '$apartmentId' was not found")
        return ServiceCharge.create(
            sourceServiceRequestId = request.id,
            buildingId = apartment.buildingId,
            title = request.title,
            amount = amount,
            target = target,
            targetApartmentId = apartment.id.takeIf {
                target == ServiceChargeTarget.SPECIFIC_UNIT
            },
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
