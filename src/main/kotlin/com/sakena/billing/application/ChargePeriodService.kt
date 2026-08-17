package com.sakena.billing.application

import com.sakena.billing.application.command.AddChargeItemCommand
import com.sakena.billing.application.command.CreateChargePeriodCommand
import com.sakena.billing.application.command.UpdateChargePeriodCommand
import com.sakena.billing.domain.ChargeItemNotFoundException
import com.sakena.billing.domain.ChargeItemRepository
import com.sakena.billing.domain.ChargePeriodNotFoundException
import com.sakena.billing.domain.ChargePeriodRepository
import com.sakena.billing.domain.UnitInvoiceRepository
import com.sakena.billing.domain.model.ChargeItem
import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargePeriod
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.property.domain.ApartmentNotFoundException
import com.sakena.property.domain.ApartmentRepository
import com.sakena.property.domain.BuildingAccess
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
import com.sakena.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service for defining charge periods and the cost lines inside
 * them. Issuing a period and collecting payments lives in [InvoiceService].
 */
@Service
@Transactional
class ChargePeriodService(
    private val periodRepository: ChargePeriodRepository,
    private val itemRepository: ChargeItemRepository,
    private val invoiceRepository: UnitInvoiceRepository,
    private val apartmentRepository: ApartmentRepository,
    private val buildingAccess: BuildingAccess,
) {

    fun create(command: CreateChargePeriodCommand, managerId: UserId): ChargePeriod {
        buildingAccess.requireManagerAccess(command.buildingId, managerId)
        val period = ChargePeriod.create(
            buildingId = command.buildingId,
            title = command.title,
            type = command.type,
            startsOn = command.startsOn,
            endsOn = command.endsOn,
        )
        return periodRepository.save(period)
    }

    fun update(
        id: ChargePeriodId,
        command: UpdateChargePeriodCommand,
        managerId: UserId,
    ): ChargePeriod {
        val period = requireManagedPeriod(id, managerId)
        period.reschedule(command.title, command.startsOn, command.endsOn)
        return periodRepository.save(period)
    }

    fun close(id: ChargePeriodId, managerId: UserId): ChargePeriod {
        val period = requireManagedPeriod(id, managerId)
        period.close()
        return periodRepository.save(period)
    }

    fun delete(id: ChargePeriodId, managerId: UserId) {
        val period = requireManagedPeriod(id, managerId)
        if (invoiceRepository.existsByPeriod(id)) {
            throw DomainConflictException("Cannot delete a charge period that already has invoices")
        }
        itemRepository.findAllByPeriod(id).forEach { itemRepository.deleteById(it.id) }
        periodRepository.deleteById(period.id)
    }

    fun addItem(
        periodId: ChargePeriodId,
        command: AddChargeItemCommand,
        managerId: UserId,
    ): ChargeItem {
        val period = requireManagedPeriod(periodId, managerId)
        if (!period.editable) {
            throw DomainConflictException("Cannot add cost lines to a period that is already ${period.status}")
        }
        command.targetApartmentId?.let { apartmentId ->
            val apartment = apartmentRepository.findById(apartmentId)
                ?: throw ApartmentNotFoundException(apartmentId)
            if (apartment.buildingId != period.buildingId) {
                throw DomainConflictException("The target apartment does not belong to this charge period's building")
            }
        }
        val item = ChargeItem.create(
            periodId = period.id,
            title = command.title,
            amount = command.amount,
            kind = command.kind,
            allocation = command.allocation,
            targetApartmentId = command.targetApartmentId,
        )
        return itemRepository.save(item)
    }

    fun removeItem(periodId: ChargePeriodId, itemId: ChargeItemId, managerId: UserId) {
        val period = requireManagedPeriod(periodId, managerId)
        if (!period.editable) {
            throw DomainConflictException("Cannot remove cost lines from a period that is already ${period.status}")
        }
        val item = itemRepository.findById(itemId) ?: throw ChargeItemNotFoundException(itemId)
        if (item.periodId != periodId) throw ChargeItemNotFoundException(itemId)
        itemRepository.deleteById(itemId)
    }

    @Transactional(readOnly = true)
    fun getById(id: ChargePeriodId, managerId: UserId): ChargePeriod =
        requireManagedPeriod(id, managerId)

    @Transactional(readOnly = true)
    fun getAll(buildingId: BuildingId?, managerId: UserId): List<ChargePeriod> {
        val managedBuildingId = buildingAccess.managedBuildingId(managerId)
        if (buildingId != null && buildingId != managedBuildingId) {
            throw DomainForbiddenException("You do not manage this building")
        }
        return periodRepository.findAll(managedBuildingId)
    }

    @Transactional(readOnly = true)
    fun getItems(periodId: ChargePeriodId, managerId: UserId): List<ChargeItem> {
        requireManagedPeriod(periodId, managerId)
        return itemRepository.findAllByPeriod(periodId)
    }

    private fun requireManagedPeriod(id: ChargePeriodId, managerId: UserId): ChargePeriod {
        val period = requirePeriod(id)
        buildingAccess.requireManagerAccess(period.buildingId, managerId)
        return period
    }

    private fun requirePeriod(id: ChargePeriodId): ChargePeriod =
        periodRepository.findById(id) ?: throw ChargePeriodNotFoundException(id)
}
