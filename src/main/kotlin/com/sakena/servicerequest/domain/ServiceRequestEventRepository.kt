package com.sakena.servicerequest.domain

interface ServiceRequestEventRepository {
    fun save(event: ServiceRequestEvent): ServiceRequestEvent
}
