package com.sakena.servicerequest.domain

enum class ServiceRequestStatus {
    PENDING,      // Waiting for manager review
    APPROVED,     // Manager approved, may be assigned
    IN_PROGRESS,  // Assigned to worker
    COMPLETED,    // Resolved
    REJECTED      // Manager rejected
}
