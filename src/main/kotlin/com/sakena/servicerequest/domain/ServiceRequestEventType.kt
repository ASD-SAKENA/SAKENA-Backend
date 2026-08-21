package com.sakena.servicerequest.domain

enum class ServiceRequestEventType {
    CREATED,
    UPDATED,
    APPROVED,
    REJECTED,
    ASSIGNED,
    STARTED_PROGRESS,
    COMPLETED,
    CONFIRMED,
    REJECTED_COMPLETION,
    COST_RESPONSIBILITY_ASSIGNED,
    SETTLED
}
