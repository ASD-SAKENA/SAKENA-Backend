package com.sakena.wallet.application

import com.sakena.billing.domain.ServiceChargeRepository
import com.sakena.billing.domain.model.ServiceCharge
import com.sakena.billing.domain.model.ServiceChargeTarget
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceCostResponsibility
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import com.sakena.wallet.application.command.FundWalletCommand
import com.sakena.wallet.domain.WalletRepository
import com.sakena.wallet.domain.WalletTransactionRepository
import com.sakena.wallet.domain.model.TransactionCategory
import com.sakena.wallet.domain.model.TransactionDirection
import com.sakena.wallet.domain.model.Wallet
import com.sakena.wallet.domain.model.WalletTransaction
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
    private val apartmentRepository = mockk<ApartmentRepository>(relaxed = true)
    private val serviceChargeRepository = mockk<ServiceChargeRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val service = WalletService(
        walletRepository,
        transactionRepository,
        serviceRequestRepository,
        apartmentRepository,
        serviceChargeRepository,
        userRepository,
    )

    private val buildingId = BuildingId.new()
    private val managerUser = user(Role.MANAGER, buildingId)
    private val manager = managerUser.id
    private val worker = UserId.generate()

    init {
        every { userRepository.findById(manager) } returns managerUser
    }

    private fun completedRequest(
        responsibility: ServiceCostResponsibility? = ServiceCostResponsibility.BUILDING_WALLET,
        requestingApartmentId: ApartmentId? = ApartmentId.new(),
    ): ServiceRequest {
        val created = ServiceRequest.create(
            title = "Fix kitchen leak",
            description = "The sink is leaking",
            location = "Unit 12",
            createdBy = UserId.generate(),
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
            requestingApartmentId = requestingApartmentId,
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
        // No requesting apartment: settles against the acting manager's own building.
        val request = completedRequest(requestingApartmentId = null)
        val building = Wallet.createBuilding(buildingId)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { walletRepository.findBuildingWallet(buildingId) } returns building
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
        val request = completedRequest(requestingApartmentId = null)
        val building = Wallet.createBuilding(buildingId)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { walletRepository.findBuildingWallet(buildingId) } returns building
        every { walletRepository.findByOwner(worker) } returns null
        every { walletRepository.save(any()) } answers { firstArg() }
        val ledger = mutableListOf<WalletTransaction>()
        every { transactionRepository.save(capture(ledger)) } answers { ledger.last() }

        service.settleServiceRequest(request.id, manager)

        assertEquals(2, ledger.size)
        assertEquals(TransactionDirection.DEBIT, ledger.first().direction)
        assertEquals(TransactionCategory.WAGE_SETTLEMENT, ledger.first().category)
    }

    @Test
    fun `settle is rejected for a manager who does not administer the request's building`() {
        val request = completedRequest()
        val apartment = apartment(request.requestingApartmentId!!)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(apartment.id) } returns apartment

        assertFailsWith<DomainForbiddenException> {
            service.settleServiceRequest(request.id, manager)
        }
        verify(exactly = 0) { walletRepository.save(any()) }
    }

    @Test
    fun `settle is rejected for a non-manager`() {
        val resident = user(Role.RESIDENT)
        val request = completedRequest(requestingApartmentId = null)
        every { userRepository.findById(resident.id) } returns resident
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainForbiddenException> {
            service.settleServiceRequest(request.id, resident.id)
        }
        verify(exactly = 0) { walletRepository.save(any()) }
    }

    @Test
    fun `recording a building expense debits the account and logs it`() {
        val building = Wallet.createBuilding(buildingId)
        every { walletRepository.findBuildingWallet(buildingId) } returns building
        every { walletRepository.save(any()) } answers { firstArg() }
        val ledger = slot<WalletTransaction>()
        every { transactionRepository.save(capture(ledger)) } answers { ledger.captured }

        service.recordBuildingTransaction(
            com.sakena.wallet.application.command.RecordBuildingTransactionCommand(
                direction = TransactionDirection.DEBIT,
                category = TransactionCategory.OPERATING_EXPENSE,
                amount = BigDecimal("400000"),
                description = "Boiler room service",
            ),
            requesterManagedBuildingId = buildingId,
        )

        assertEquals(BigDecimal("-400000"), building.balance)
        assertEquals("Boiler room service", ledger.captured.description)
        assertEquals(building.balance, ledger.captured.balanceAfter)
    }

    @Test
    fun `recording a building expense is rejected for a caller with no managed building`() {
        assertFailsWith<DomainForbiddenException> {
            service.recordBuildingTransaction(
                com.sakena.wallet.application.command.RecordBuildingTransactionCommand(
                    direction = TransactionDirection.DEBIT,
                    category = TransactionCategory.OPERATING_EXPENSE,
                    amount = BigDecimal("400000"),
                    description = "Boiler room service",
                ),
                requesterManagedBuildingId = null,
            )
        }
        verify(exactly = 0) { walletRepository.save(any()) }
    }

    @Test
    fun `building transactions reject the personal wallet funding category`() {
        assertFailsWith<DomainValidationException> {
            service.recordBuildingTransaction(
                com.sakena.wallet.application.command.RecordBuildingTransactionCommand(
                    direction = TransactionDirection.CREDIT,
                    category = TransactionCategory.WALLET_FUNDING,
                    amount = BigDecimal("400000"),
                    description = "Invalid building top-up",
                ),
                requesterManagedBuildingId = buildingId,
            )
        }

        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun `resident funds an existing wallet and records the credit`() {
        val resident = user(Role.RESIDENT)
        val wallet = Wallet.createForUser(resident.id).apply { credit(BigDecimal("100000")) }
        every { userRepository.findById(resident.id) } returns resident
        every { walletRepository.findByOwner(resident.id) } returns wallet
        every { walletRepository.save(any()) } answers { firstArg() }
        val ledger = slot<WalletTransaction>()
        every { transactionRepository.save(capture(ledger)) } answers { ledger.captured }

        val funded = service.fundMyWallet(FundWalletCommand(BigDecimal("250000")), resident.id)

        assertEquals(BigDecimal("350000"), funded.balance)
        assertEquals(TransactionDirection.CREDIT, ledger.captured.direction)
        assertEquals(TransactionCategory.WALLET_FUNDING, ledger.captured.category)
        assertEquals(BigDecimal("250000"), ledger.captured.amount)
        assertEquals(BigDecimal("350000"), ledger.captured.balanceAfter)
    }

    @Test
    fun `first funding creates the resident wallet`() {
        val resident = user(Role.RESIDENT)
        every { userRepository.findById(resident.id) } returns resident
        every { walletRepository.findByOwner(resident.id) } returns null
        val savedWallet = slot<Wallet>()
        every { walletRepository.save(capture(savedWallet)) } answers { savedWallet.captured }
        every { transactionRepository.save(any()) } answers { firstArg() }

        val funded = service.fundMyWallet(FundWalletCommand(BigDecimal("500000")), resident.id)

        assertEquals(resident.id, savedWallet.captured.ownerUserId)
        assertEquals(BigDecimal("500000"), funded.balance)
        verify(exactly = 1) { transactionRepository.save(any()) }
    }

    @Test
    fun `non-resident cannot fund a personal wallet`() {
        every { userRepository.findById(manager) } returns managerUser

        assertFailsWith<DomainForbiddenException> {
            service.fundMyWallet(FundWalletCommand(BigDecimal("500000")), manager)
        }

        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun `non-positive funding is rejected without writes`() {
        val resident = user(Role.RESIDENT)
        val wallet = Wallet.createForUser(resident.id)
        every { userRepository.findById(resident.id) } returns resident
        every { walletRepository.findByOwner(resident.id) } returns wallet

        assertFailsWith<DomainValidationException> {
            service.fundMyWallet(FundWalletCommand(BigDecimal.ZERO), resident.id)
        }

        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
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
        val request = completedRequest(requestingApartmentId = null)
        val noCost = request.copy(completionCost = null)
        every { serviceRequestRepository.findById(noCost.id) } returns noCost

        assertFailsWith<DomainValidationException> {
            service.settleServiceRequest(noCost.id, manager)
        }
    }

    @Test
    fun `settle rejects a completed request without cost responsibility`() {
        val request = completedRequest(responsibility = null, requestingApartmentId = null)
        every { serviceRequestRepository.findById(request.id) } returns request

        assertFailsWith<DomainValidationException> {
            service.settleServiceRequest(request.id, manager)
        }

        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `all-units settlement queues the building cost and pays the worker`() {
        val request = completedRequest(ServiceCostResponsibility.ALL_UNITS)
        val apartment = apartment(request.requestingApartmentId!!, buildingId)
        val buildingWallet = Wallet.createBuilding(buildingId)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { walletRepository.findBuildingWallet(buildingId) } returns buildingWallet
        every { walletRepository.findByOwner(worker) } returns null
        every { walletRepository.save(any()) } answers { firstArg() }
        every { serviceRequestRepository.save(any()) } answers { firstArg() }
        val queuedCharge = slot<ServiceCharge>()
        every { serviceChargeRepository.save(capture(queuedCharge)) } answers { queuedCharge.captured }

        service.settleServiceRequest(request.id, manager)

        assertEquals(request.id, queuedCharge.captured.sourceServiceRequestId)
        assertEquals(apartment.buildingId, queuedCharge.captured.buildingId)
        assertEquals(ServiceChargeTarget.ALL_UNITS, queuedCharge.captured.target)
        assertEquals(null, queuedCharge.captured.targetApartmentId)
        assertEquals(BigDecimal("250000.0"), queuedCharge.captured.amount)
        assertEquals(BigDecimal("-250000.0"), buildingWallet.balance)
        verify(exactly = 1) { serviceRequestRepository.save(match { it.status.name == "SETTLED" }) }
    }

    @Test
    fun `all-units settlement rejects a missing requesting apartment without writes`() {
        val request = completedRequest(ServiceCostResponsibility.ALL_UNITS)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(request.requestingApartmentId!!) } returns null

        assertFailsWith<EntityNotFoundException> {
            service.settleServiceRequest(request.id, manager)
        }

        verify(exactly = 0) { serviceChargeRepository.save(any()) }
        verify(exactly = 0) { walletRepository.save(any()) }
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { serviceRequestRepository.save(any()) }
    }

    @Test
    fun `requesting-unit settlement queues a targeted cost and pays the worker`() {
        val request = completedRequest(ServiceCostResponsibility.REQUESTING_UNIT)
        val apartment = apartment(request.requestingApartmentId!!, buildingId)
        val buildingWallet = Wallet.createBuilding(buildingId)
        every { serviceRequestRepository.findById(request.id) } returns request
        every { apartmentRepository.findById(apartment.id) } returns apartment
        every { walletRepository.findBuildingWallet(buildingId) } returns buildingWallet
        every { walletRepository.findByOwner(worker) } returns null
        every { walletRepository.save(any()) } answers { firstArg() }
        every { serviceRequestRepository.save(any()) } answers { firstArg() }
        val queuedCharge = slot<ServiceCharge>()
        every { serviceChargeRepository.save(capture(queuedCharge)) } answers { queuedCharge.captured }

        service.settleServiceRequest(request.id, manager)

        assertEquals(request.id, queuedCharge.captured.sourceServiceRequestId)
        assertEquals(apartment.buildingId, queuedCharge.captured.buildingId)
        assertEquals(ServiceChargeTarget.SPECIFIC_UNIT, queuedCharge.captured.target)
        assertEquals(apartment.id, queuedCharge.captured.targetApartmentId)
        assertEquals(BigDecimal("250000.0"), queuedCharge.captured.amount)
        assertEquals(BigDecimal("-250000.0"), buildingWallet.balance)
        verify(exactly = 1) { serviceRequestRepository.save(match { it.status.name == "SETTLED" }) }
    }

    private fun apartment(id: ApartmentId, ownerBuildingId: BuildingId = BuildingId.new()): Apartment = Apartment.reconstitute(
        id = id,
        buildingId = ownerBuildingId,
        unitNumber = "12",
        floorNumber = 1,
        areaSquareMeters = BigDecimal("90"),
        bedrooms = 2,
        createdAt = java.time.Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = java.time.Instant.parse("2026-01-15T10:00:00Z"),
    )

    private fun user(role: Role, managedBuildingId: BuildingId? = null): User {
        val now = java.time.Instant.parse("2026-01-15T10:00:00Z")
        return User.reconstitute(
            id = UserId.generate(),
            username = "${role.name.lowercase()}-user-${UserId.generate()}",
            email = "${role.name.lowercase()}-${UserId.generate()}@example.com",
            passwordHash = "hash",
            role = role,
            createdAt = now,
            updatedAt = now,
            active = true,
            managedBuildingId = managedBuildingId ?: if (role == Role.MANAGER) BuildingId.new() else null,
        )
    }
}
