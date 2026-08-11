package com.sakena.residency.application.command

import com.sakena.residency.domain.model.TenancyType
import com.sakena.user.domain.UserId

data class StartResidencyCommand(
    val residentId: UserId,
    val tenancy: TenancyType,
)
