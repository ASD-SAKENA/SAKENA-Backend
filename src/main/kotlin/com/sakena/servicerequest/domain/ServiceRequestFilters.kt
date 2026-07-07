package com.sakena.servicerequest.domain

import com.sakena.user.domain.UserId
import java.time.Instant

/**
 * Unified filter object for querying service requests.
 * All fields are optional — only non-null values are applied as predicates.
 */
data class ServiceRequestFilters(
    val createdBy: UserId? = null,
    val updatedBy: UserId? = null,
    val assignedTo: UserId? = null,
    val status: ServiceRequestStatus? = null,
    val categoryGroup: ServiceCategoryGroup? = null,
    val subCategory: ServiceSubCategory? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
    val updatedFrom: Instant? = null,
    val updatedTo: Instant? = null
)
