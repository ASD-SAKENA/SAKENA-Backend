package com.sakena.wallet.application

import com.sakena.property.domain.model.ApartmentId
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.WalletTransactionRepository
import com.sakena.wallet.domain.model.Wallet
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletServiceTest {

    private val walletRepository = mockk<WalletRepository>(relaxed = true)
    private val transactionRepository = mockk<WalletTransactionRepository>(relaxed = true)
    private val serviceRequestRepository = mockk<ServiceRequestRepository>(relaxed = true)
    private val service = WalletService(
        walletRepository,
        transactionRepository,
        serviceRequestRepository,
    )

    private val manager = UserId.generate()
    private val worker = UserId.generate()

    private fun completedRequest(
        responsibility: ServiceCostResponsibility? = ServiceCostResponsibility.BUILDING_WALLET,
    ): ServiceRequest {
        val created = ServiceRequest.create(
            title = "Fix kitchen leak",
            description = "The sink is leaking",
            location = "Unit 12",
            createdBy = UserId.generate(),
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
            requestingApartmentId = ApartmentId.new(),
        )
        val completed = created
            .approve(manager)
            .assignTo(worker, manager)
            .startProgress()
            .complete(worker, "Replaced the valve", 250_000.0)
        return responsibility?.let { completed.assignCostResponsibility(it, manager) } ?: completed
    }

    @Test
    fun `settle debits the building, credits the worker and marks the request settled`() {
        val request = completedRequest()
        val building = Wallet.createBuilding()
        every { serviceRequestRepository.findById(request.id) } returns request
        every { walletRepository.findBuildingWallet() } returns building
        every { walletRepository.findByOwner(worker) } returns null
        val savedWallets = mutableListOf<Wallet>()
        every { walletRepository.save(capture(savedWallets)) } answers { savedWallets.last() }
        val savedRequest = slot<ServiceRequest>()
        every { serviceRequestRepository.save(capture(savedRequest)) } answers { savedRequest.captured }

        service.settleServiceRequest(request.id, manager)

        assertEquals(BigDecimal("-250000.0"), building.balance)
        val workerWallet = savedWallets.first { it.ownerUserId == worker }
        assertEquals(BigDecimal("250000.0"), workerWallet.balance)
        assertEquals("SETTLED", savedRequest.captured.status.name)
    }

    @Test
    fun `settle writes a ledger line on both wallets`() {
        val request = completedRequest()
        val building = Wallet.createBuilding()
        every { serviceRequestRepository.findById(request.id) } returns request
        every { walletRepository.findBuildingWallet() } returns building
        every { walletRepository.findByOwner(worker) } returns null
        every { walletRepository.save(any()) } answers { firstArg() }
        val ledger = mutableListOf<com.sakena.wallet.domain.model.WalletTransaction>()
        every { transactionRepository.save(capture(ledger)) } answers { ledger.last() }

        service.settleServiceRequest(request.id, manager)

        assertEquals(2, ledger.size)
        assertEquals(
            com.sakena.wallet.domain.model.TransactionDirection.DEBIT,
            ledger.first().direction,
        )
        assertEquals(
            com.sakena.wallet.domain.model.TransactionCategory.WAGE_SETTLEMENT,
            ledger.first().category,
        )
    }

    @Test
    fun `recording a building expense debits the account and logs it`() {
        val building = Wallet.createBuilding()
        every { walletRepository.findBuildingWallet() } returns building
        every { walletRepository.save(any()) } answers { firstArg() }
        val ledger = slot<com.sakena.wallet.domain.model.WalletTransaction>()
        every { transactionRepository.save(capture(ledger)) } answers { ledger.captured }

        service.recordBuildingTransaction(
            com.sakena.wallet.application.command.RecordBuildingTransactionCommand(
                direction = com.sakena.wallet.domain.model.TransactionDirection.DEBIT,
                category = com.sakena.wallet.domain.model.TransactionCategory.OPERATING_EXPENSE,
                amount = BigDecimal("400000"),
                description = "Boiler room service",
            ),
        )

        assertEquals(BigDecimal("-400000"), building.balance)
        assertEquals("Boiler room service", ledger.captured.description)
        assertEquals(building.balance, ledger.captured.balanceAfter)
    }

    @Test
    fun `settle rejects a request that is not completed`() {
        val request = ServiceRequest.create(
            title = "Fix lamp",
            description = "Stairway lamp is broken",
            location = null,
            createdBy = UserId.generate(),
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.ELECTRICAL,
        )
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainValidationException> {
            service.settleServiceRequest(request.id, manager)
        }
        verify(exactly = 0) { walletRepository.save(any()) }
    }

    @Test
    fun `settle rejects a completed request without a cost`() {
        val request = completedRequest()
        val noCost = request.copy(completionCost = null)
        every { serviceRequestRepository.findById(noCost.id) } returns noCost

        assertFailsWith<DomainValidationException> {
            service.settleServiceRequest(noCost.id, manager)
        }
    }

    @Test
    fun `settle rejects a completed request without cost responsibility`() {
        val request = completedRequest(responsibility = null)
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainValidationException> {
            service.settleServiceRequest(request.id, manager)
        }

        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `wallet settlement rejects responsibilities that require billing without writes`() {
        listOf(
            ServiceCostResponsibility.ALL_UNITS,
            ServiceCostResponsibility.REQUESTING_UNIT,
        ).forEach { responsibility ->
            val request = completedRequest(responsibility)
            every { serviceRequestRepository.findById(request.id) } returns request

            assertFailsWith<DomainConflictException> {
                service.settleServiceRequest(request.id, manager)
            }
        }

        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }
}
