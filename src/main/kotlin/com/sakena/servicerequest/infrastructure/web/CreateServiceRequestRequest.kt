package com.sakena.servicerequest.infrastructure.web

import com.sakena.servicerequest.domain.ServiceCategoryGroup
import com.sakena.servicerequest.domain.ServiceSubCategory
import com.sakena.shared.domain.DomainValidationException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateServiceRequestRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 200, message = "Title must be at most 200 characters")
    val title: String? = null,

    @field:NotBlank(message = "Description is required")
    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,

    @field:Size(max = 255, message = "Location must be at most 255 characters")
    val location: String? = null,

    @field:NotNull(message = "Category group is required")
    val categoryGroup: ServiceCategoryGroup? = null,

    @field:NotNull(message = "Sub category is required")
    val subCategory: ServiceSubCategory? = null
) {
    fun validateOrThrow() {
        if (title.isNullOrBlank()) {
            throw DomainValidationException("Title is required")
        }
        if (description.isNullOrBlank()) {
            throw DomainValidationException("Description is required")
        }
        if (location != null && location.isBlank()) {
            throw DomainValidationException("Location cannot be blank when provided")
        }
        if (categoryGroup == null) {
            throw DomainValidationException("Category group is required")
        }
        if (subCategory == null) {
            throw DomainValidationException("Sub category is required")
        }
    }
}
