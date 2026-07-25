package com.sakena.billing.infrastructure.persistence

import com.sakena.billing.domain.ChargeItemRepository
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.UnitInvoice
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Adapters implementing the billing domain ports on top of Spring Data JPA.
 * The only place that knows about the billing JPA entities.
 */
@Component
class ChargePeriodRepositoryAdapter(
    private val jpaRepository: ChargePeriodJpaRepository,
) : ChargePeriodRepository {

    override fun save(period: ChargePeriod): ChargePeriod {
        val saved = jpaRepository.save(BillingEntityMappers.toEntity(period))
        return BillingEntityMappers.toDomain(saved)
    }

    override fun findById(id: ChargePeriodId): ChargePeriod? =
        jpaRepository.findByIdOrNull(id.value)?.let(BillingEntityMappers::toDomain)

    override fun findAll(buildingId: BuildingId?): List<ChargePeriod> {
        val entities = buildingId
            ?.let { jpaRepository.findAllByBuildingIdOrderByStartsOnDesc(it.value) }
            ?: jpaRepository.findAllByOrderByStartsOnDesc()
        return entities.map(BillingEntityMappers::toDomain)
    }

    override fun deleteById(id: ChargePeriodId) = jpaRepository.deleteById(id.value)
}

@Component
class ChargeItemRepositoryAdapter(
    private val jpaRepository: ChargeItemJpaRepository,
) : ChargeItemRepository {

    override fun save(item: ChargeItem): ChargeItem {
        val saved = jpaRepository.save(BillingEntityMappers.toEntity(item))
        return BillingEntityMappers.toDomain(saved)
    }

    override fun findById(id: ChargeItemId): ChargeItem? =
        jpaRepository.findByIdOrNull(id.value)?.let(BillingEntityMappers::toDomain)

    override fun findAllByPeriod(periodId: ChargePeriodId): List<ChargeItem> =
        jpaRepository.findAllByPeriodIdOrderByCreatedAt(periodId.value)
            .map(BillingEntityMappers::toDomain)

    override fun deleteById(id: ChargeItemId) = jpaRepository.deleteById(id.value)
}

@Component
class UnitInvoiceRepositoryAdapter(
    private val jpaRepository: UnitInvoiceJpaRepository,
) : UnitInvoiceRepository {

    override fun saveAll(invoices: List<UnitInvoice>): List<UnitInvoice> =
        jpaRepository.saveAll(invoices.map(BillingEntityMappers::toEntity))
            .map(BillingEntityMappers::toDomain)

    override fun save(invoice: UnitInvoice): UnitInvoice {
        val saved = jpaRepository.save(BillingEntityMappers.toEntity(invoice))
        return BillingEntityMappers.toDomain(saved)
    }

    override fun findById(id: UnitInvoiceId): UnitInvoice? =
        jpaRepository.findByIdOrNull(id.value)?.let(BillingEntityMappers::toDomain)

    override fun findAllByPeriod(periodId: ChargePeriodId): List<UnitInvoice> =
        jpaRepository.findAllByPeriodIdOrderByIssuedAt(periodId.value)
            .map(BillingEntityMappers::toDomain)

    override fun findAllByApartment(apartmentId: ApartmentId): List<UnitInvoice> =
        jpaRepository.findAllByApartmentIdOrderByIssuedAtDesc(apartmentId.value)
            .map(BillingEntityMappers::toDomain)

    override fun existsByPeriod(periodId: ChargePeriodId): Boolean =
        jpaRepository.existsByPeriodId(periodId.value)
}
