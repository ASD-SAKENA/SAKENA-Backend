package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceRequestStatus
import com.sakena.servicerequest.domain.ServiceSubCategory
import java.time.Instant

data class GetAllServiceRequestsQuery(
    val status: ServiceRequestStatus? = null,
    val categoryGroup: ServiceCategoryGroup? = null,
    val subCategory: ServiceSubCategory? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
    val updatedFrom: Instant? = null,
    val updatedTo: Instant? = null
)
