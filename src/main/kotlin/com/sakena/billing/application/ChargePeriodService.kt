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
import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainForbiddenException
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
) {

    fun create(command: CreateChargePeriodCommand, requesterManagedBuildingId: BuildingId?): ChargePeriod {
        if (requesterManagedBuildingId != command.buildingId) {
            throw DomainForbiddenException("You do not manage building '${command.buildingId}'")
        }
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
        requesterManagedBuildingId: BuildingId?,
    ): ChargePeriod {
        val period = requireOwnedPeriod(id, requesterManagedBuildingId)
        period.reschedule(command.title, command.startsOn, command.endsOn)
        return periodRepository.save(period)
    }

    fun close(id: ChargePeriodId, requesterManagedBuildingId: BuildingId?): ChargePeriod {
        val period = requireOwnedPeriod(id, requesterManagedBuildingId)
        period.close()
        return periodRepository.save(period)
    }

    fun delete(id: ChargePeriodId, requesterManagedBuildingId: BuildingId?) {
        val period = requireOwnedPeriod(id, requesterManagedBuildingId)
        if (invoiceRepository.existsByPeriod(id)) {
            throw DomainConflictException("Cannot delete a charge period that already has invoices")
        }
        itemRepository.findAllByPeriod(id).forEach { itemRepository.deleteById(it.id) }
        periodRepository.deleteById(period.id)
    }

    fun addItem(
        periodId: ChargePeriodId,
        command: AddChargeItemCommand,
        requesterManagedBuildingId: BuildingId?,
    ): ChargeItem {
        val period = requireOwnedPeriod(periodId, requesterManagedBuildingId)
        if (!period.editable) {
            throw DomainConflictException("Cannot add cost lines to a period that is already ${period.status}")
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

    fun removeItem(periodId: ChargePeriodId, itemId: ChargeItemId, requesterManagedBuildingId: BuildingId?) {
        val period = requireOwnedPeriod(periodId, requesterManagedBuildingId)
        if (!period.editable) {
            throw DomainConflictException("Cannot remove cost lines from a period that is already ${period.status}")
        }
        val item = itemRepository.findById(itemId) ?: throw ChargeItemNotFoundException(itemId)
        if (item.periodId != periodId) throw ChargeItemNotFoundException(itemId)
        itemRepository.deleteById(itemId)
    }

    @Transactional(readOnly = true)
    fun getById(id: ChargePeriodId): ChargePeriod = requirePeriod(id)

    @Transactional(readOnly = true)
    fun getAll(buildingId: BuildingId?): List<ChargePeriod> = periodRepository.findAll(buildingId)

    @Transactional(readOnly = true)
    fun getItems(periodId: ChargePeriodId): List<ChargeItem> {
        requirePeriod(periodId)
        return itemRepository.findAllByPeriod(periodId)
    }

    private fun requirePeriod(id: ChargePeriodId): ChargePeriod =
        periodRepository.findById(id) ?: throw ChargePeriodNotFoundException(id)

    private fun requireOwnedPeriod(id: ChargePeriodId, requesterManagedBuildingId: BuildingId?): ChargePeriod {
        val period = requirePeriod(id)
        if (requesterManagedBuildingId != period.buildingId) {
            throw DomainForbiddenException("You do not manage building '${period.buildingId}'")
        }
        return period
    }
}
