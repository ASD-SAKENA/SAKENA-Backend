package com.sakena.wallet.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequest
import com.sakena.servicerequest.domain.ServiceRequestRepository
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import com.sakena.wallet.domain.WalletRepository
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
    private val serviceRequestRepository = mockk<ServiceRequestRepository>(relaxed = true)
    private val service = WalletService(walletRepository, serviceRequestRepository)

    private val manager = UserId.generate()
    private val worker = UserId.generate()

    private fun completedRequest(): ServiceRequest {
        val created = ServiceRequest.create(
            title = "Fix kitchen leak",
            description = "The sink is leaking",
            location = "Unit 12",
            createdBy = UserId.generate(),
            categoryGroup = ServiceCategoryGroup.FACILITIES,
            subCategory = ServiceSubCategory.PLUMBING,
        )
        return created
            .approve(manager)
            .assignTo(worker, manager)
            .startProgress()
            .complete(worker, "Replaced the valve", 250_000.0)
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
}
