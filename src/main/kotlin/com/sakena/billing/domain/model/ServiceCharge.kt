package com.sakena.billing.domain.model

import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import java.math.BigDecimal
import java.time.Instant

enum class ServiceChargeTarget {
    ALL_UNITS,
    SPECIFIC_UNIT,
}

/**
 * A service cost waiting to be attached to a building charge period.
 * The source request is unique in persistence, preventing duplicate billing.
 */
class ServiceCharge private constructor(
    val id: ServiceChargeId,
    val sourceServiceRequestId: ServiceRequestId,
    val buildingId: BuildingId,
    val title: String,
    val amount: BigDecimal,
    val target: ServiceChargeTarget,
    val targetApartmentId: ApartmentId?,
    attachedPeriodId: ChargePeriodId?,
    val createdAt: Instant,
    attachedAt: Instant?,
) {
    var attachedPeriodId: ChargePeriodId? = attachedPeriodId
        private set

    var attachedAt: Instant? = attachedAt
        private set

    val pending: Boolean get() = attachedPeriodId == null

    fun createChargeItemFor(periodId: ChargePeriodId): ChargeItem =
        ChargeItem.create(
            periodId = periodId,
            title = title,
            amount = amount,
            kind = ChargeItemKind.EXTRAORDINARY_EXPENSE,
            allocation = when (target) {
                ServiceChargeTarget.ALL_UNITS -> CostAllocation.EQUAL
                ServiceChargeTarget.SPECIFIC_UNIT -> CostAllocation.SPECIFIC_UNIT
            },
            targetApartmentId = targetApartmentId,
        )

    fun attachTo(periodId: ChargePeriodId) {
        if (!pending) {
            throw DomainConflictException("Service charge has already been attached to a charge period")
        }
        attachedPeriodId = periodId
        attachedAt = Instant.now()
    }

    companion object {
        const val MAX_TITLE_LENGTH = 200

        fun create(
            sourceServiceRequestId: ServiceRequestId,
            buildingId: BuildingId,
            title: String,
            amount: BigDecimal,
            target: ServiceChargeTarget,
            targetApartmentId: ApartmentId? = null,
        ): ServiceCharge {
            validateTarget(target, targetApartmentId)
            return ServiceCharge(
                id = ServiceChargeId.new(),
                sourceServiceRequestId = sourceServiceRequestId,
                buildingId = buildingId,
                title = validateTitle(title),
                amount = validateAmount(amount),
                target = target,
                targetApartmentId = targetApartmentId,
                attachedPeriodId = null,
                createdAt = Instant.now(),
                attachedAt = null,
            )
        }

        fun reconstitute(
            id: ServiceChargeId,
            sourceServiceRequestId: ServiceRequestId,
            buildingId: BuildingId,
            title: String,
            amount: BigDecimal,
            target: ServiceChargeTarget,
            targetApartmentId: ApartmentId?,
            attachedPeriodId: ChargePeriodId?,
            createdAt: Instant,
            attachedAt: Instant?,
        ): ServiceCharge = ServiceCharge(
            id = id,
            sourceServiceRequestId = sourceServiceRequestId,
            buildingId = buildingId,
            title = title,
            amount = amount,
            target = target,
            targetApartmentId = targetApartmentId,
            attachedPeriodId = attachedPeriodId,
            createdAt = createdAt,
            attachedAt = attachedAt,
        )

        private fun validateTarget(
            target: ServiceChargeTarget,
            targetApartmentId: ApartmentId?,
        ) {
            when (target) {
                ServiceChargeTarget.ALL_UNITS -> if (targetApartmentId != null) {
                    throw DomainValidationException("All-units service charges cannot target an apartment")
                }

                ServiceChargeTarget.SPECIFIC_UNIT -> if (targetApartmentId == null) {
                    throw DomainValidationException("Specific-unit service charges require an apartment")
                }
            }
        }

        private fun validateTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw DomainValidationException("Service charge title must not be blank")
            if (trimmed.length > MAX_TITLE_LENGTH) {
                throw DomainValidationException(
                    "Service charge title must be at most $MAX_TITLE_LENGTH characters",
                )
            }
            return trimmed
        }

        private fun validateAmount(amount: BigDecimal): BigDecimal {
            if (amount <= BigDecimal.ZERO) {
                throw DomainValidationException("Service charge amount must be greater than zero")
            }
            return amount
        }
    }
}
