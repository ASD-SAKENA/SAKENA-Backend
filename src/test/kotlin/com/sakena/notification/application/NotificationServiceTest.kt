package com.sakena.notification.application

import com.sakena.notification.domain.NotificationRepository
import com.sakena.notification.domain.model.Notification
import com.sakena.notification.domain.model.NotificationType
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.residency.domain.ResidencyRepository
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotificationServiceTest {

    private val notificationRepository = mockk<NotificationRepository>()
    private val residencyRepository = mockk<ResidencyRepository>()
    private val service = NotificationService(notificationRepository, residencyRepository)

    @Test
    fun `notifyBuildingResidents creates one notification per active resident`() {
        val buildingId = BuildingId.new()
        val residentA = UserId.generate()
        val residentB = UserId.generate()
        every { residencyRepository.findActiveByBuilding(buildingId) } returns listOf(
            Residency.start(ApartmentId.new(), residentA, TenancyType.TENANT),
            Residency.start(ApartmentId.new(), residentB, TenancyType.OWNER_OCCUPIER),
        )
        val saved = slot<List<Notification>>()
        every { notificationRepository.saveAll(capture(saved)) } answers { saved.captured }

        val result = service.notifyBuildingResidents(
            buildingId,
            "اطلاعیه جدید",
            "قطع آب",
            NotificationType.ANNOUNCEMENT,
            "/announcements",
        )

        assertEquals(2, result.size)
        assertEquals(setOf(residentA, residentB), result.map { it.recipientId }.toSet())
        verify(exactly = 1) { notificationRepository.saveAll(any()) }
    }

    @Test
    fun `notifyBuildingResidents is a no-op when the building has no residents`() {
        val buildingId = BuildingId.new()
        every { residencyRepository.findActiveByBuilding(buildingId) } returns emptyList()

        val result = service.notifyBuildingResidents(
            buildingId,
            "اطلاعیه جدید",
            "قطع آب",
            NotificationType.ANNOUNCEMENT,
        )

        assertEquals(emptyList(), result)
        verify(exactly = 0) { notificationRepository.saveAll(any()) }
    }
}
