package com.sakena.billing.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChargePeriodJpaRepository : JpaRepository<ChargePeriodEntity, UUID> {
    fun findAllByOrderByStartsOnDesc(): List<ChargePeriodEntity>

    fun findAllByBuildingIdOrderByStartsOnDesc(buildingId: UUID): List<ChargePeriodEntity>
}

interface ChargeItemJpaRepository : JpaRepository<ChargeItemEntity, UUID> {
    fun findAllByPeriodIdOrderByCreatedAt(periodId: UUID): List<ChargeItemEntity>
}

interface ServiceChargeJpaRepository : JpaRepository<ServiceChargeEntity, UUID> {
    fun findBySourceServiceRequestId(sourceServiceRequestId: UUID): ServiceChargeEntity?

    fun findAllByBuildingIdAndChargePeriodIdIsNullOrderByCreatedAt(
        buildingId: UUID,
    ): List<ServiceChargeEntity>
}

interface UnitInvoiceJpaRepository : JpaRepository<UnitInvoiceEntity, UUID> {
    fun findAllByPeriodIdOrderByIssuedAt(periodId: UUID): List<UnitInvoiceEntity>

    fun findAllByApartmentIdOrderByIssuedAtDesc(apartmentId: UUID): List<UnitInvoiceEntity>

    fun existsByPeriodId(periodId: UUID): Boolean
}
