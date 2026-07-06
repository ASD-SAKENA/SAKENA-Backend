package com.sakena.servicerequest.application

data class CategoryOptionsResult(
    val categories: List<CategoryGroupOptionResult>
)

data class CategoryGroupOptionResult(
    val value: String,
    val label: String,
    val subCategories: List<SubCategoryOptionResult>
)

data class SubCategoryOptionResult(
    val value: String,
    val label: String
)
