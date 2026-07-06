package com.sakena.servicerequest.domain

enum class ServiceRequestStatus {
    PENDING,      // Waiting for manager review
    APPROVED,     // Manager approved
    ASSIGNED, // Manager assigned to worker
    IN_PROGRESS,  // Assigned to worker
    COMPLETED,    // Resolved
    REJECTED      // Manager rejected
}
