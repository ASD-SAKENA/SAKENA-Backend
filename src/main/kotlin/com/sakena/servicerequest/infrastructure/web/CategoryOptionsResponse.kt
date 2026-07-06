package com.sakena.servicerequest.infrastructure.web

data class CategoryOptionsResponse(
    val categories: List<CategoryGroupResponse>
)

data class CategoryGroupResponse(
    val value: String,
    val label: String,
    val subCategories: List<CategoryOptionResponse>
)

data class CategoryOptionResponse(
    val value: String,
    val label: String
)
