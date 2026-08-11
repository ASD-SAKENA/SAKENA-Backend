package com.sakena.billing.domain

import com.sakena.billing.domain.model.ChargeItemId
import com.sakena.billing.domain.model.ChargePeriodId
import com.sakena.billing.domain.model.UnitInvoiceId
import com.sakena.shared.domain.EntityNotFoundException

class ChargePeriodNotFoundException(id: ChargePeriodId) :
    EntityNotFoundException("Charge period with id '$id' was not found")

class ChargeItemNotFoundException(id: ChargeItemId) :
    EntityNotFoundException("Charge item with id '$id' was not found")

class UnitInvoiceNotFoundException(id: UnitInvoiceId) :
    EntityNotFoundException("Invoice with id '$id' was not found")
