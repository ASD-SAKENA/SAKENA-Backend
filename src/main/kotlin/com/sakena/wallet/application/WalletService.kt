package com.sakena.wallet.application

import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.BuildingId
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.wallet.application.command.FundWalletCommand
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
    private val userRepository: UserRepository,
) {

    fun settleServiceRequest(serviceRequestId: ServiceRequestId, settledBy: UserId) {
        val request = serviceRequestRepository.findById(serviceRequestId)
            ?: throw EntityNotFoundException("Service request with id '$serviceRequestId' was not found")
        val manager = userRepository.findById(settledBy)
            ?: throw EntityNotFoundException("User with id '$settledBy' was not found")
        if (manager.role != Role.MANAGER) {
            throw DomainForbiddenException("Only a manager can settle a service request")
        }

        // A request tied to an apartment settles against that apartment's
        // building — only its manager may do so. A request with no apartment
        // (staff-filed, or a resident with no unit) has no building to scope
        // to, so it settles against the acting manager's own building.
        val requestingApartmentId = request.requestingApartmentId
        val buildingId = if (requestingApartmentId != null) {
            val apartment = apartmentRepository.findById(requestingApartmentId)
                ?: throw EntityNotFoundException("Requesting apartment with id '$requestingApartmentId' was not found")
            if (manager.managedBuildingId != apartment.buildingId) {
                throw DomainForbiddenException("You do not manage the building this request belongs to")
            }
            apartment.buildingId
        } else {
            manager.managedBuildingId
                ?: throw DomainConflictException("Could not determine which building's wallet to settle against")
        }

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

        val buildingWallet = requireBuildingWallet(buildingId)
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

    /** Manager-driven credit or debit of the building account they administer. */
    fun recordBuildingTransaction(command: RecordBuildingTransactionCommand, requesterManagedBuildingId: BuildingId?): Wallet {
        if (command.category == TransactionCategory.WALLET_FUNDING) {
            throw DomainValidationException("Wallet funding is only valid for personal wallets")
        }
        val wallet = requireBuildingWallet(requireManagedBuilding(requesterManagedBuildingId))
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

    fun fundMyWallet(command: FundWalletCommand, userId: UserId): Wallet {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User with id '$userId' was not found")
        if (user.role != Role.RESIDENT) {
            throw DomainForbiddenException("Only residents can fund their wallet")
        }

        val wallet = walletRepository.findByOwner(userId) ?: Wallet.createForUser(userId)
        wallet.credit(command.amount)
        val saved = walletRepository.save(wallet)
        recordTransaction(
            saved,
            TransactionDirection.CREDIT,
            TransactionCategory.WALLET_FUNDING,
            command.amount,
            "Wallet funding",
        )
        return saved
    }

    @Transactional(readOnly = true)
    fun getBuildingWallet(requesterManagedBuildingId: BuildingId?): Wallet =
        requireBuildingWallet(requireManagedBuilding(requesterManagedBuildingId))

    @Transactional(readOnly = true)
    fun getBuildingLedger(requesterManagedBuildingId: BuildingId?): List<WalletTransaction> =
        transactionRepository.findAllByWalletNewestFirst(
            requireBuildingWallet(requireManagedBuilding(requesterManagedBuildingId)).id,
        )

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

    private fun requireBuildingWallet(buildingId: BuildingId): Wallet =
        walletRepository.findBuildingWallet(buildingId)
            ?: throw EntityNotFoundException("Building wallet was not found")

    private fun requireManagedBuilding(requesterManagedBuildingId: BuildingId?): BuildingId =
        requesterManagedBuildingId
            ?: throw DomainForbiddenException("You do not manage a building")
}
