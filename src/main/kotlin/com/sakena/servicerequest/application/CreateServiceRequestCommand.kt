package com.sakena.servicerequest.application

data class CreateServiceRequestCommand(
    val title: String,
    val description: String,
    val location: String? = null
)
