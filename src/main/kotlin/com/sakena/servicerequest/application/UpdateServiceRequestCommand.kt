package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequestId
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.user.domain.UserId

data class UpdateServiceRequestCommand(
    val serviceRequestId: ServiceRequestId,
    val title: String,
    val description: String,
    val location: String? = null,
    val categoryGroup: ServiceCategoryGroup,
    val subCategory: ServiceSubCategory,
    val userId: UserId,
)
