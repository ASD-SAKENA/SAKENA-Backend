package com.sakena.servicerequest.application

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceSubCategory

data class CreateServiceRequestCommand(
    val title: String,
    val description: String,
    val location: String? = null,
    val categoryGroup: ServiceCategoryGroup,
    val subCategory: ServiceSubCategory
)
