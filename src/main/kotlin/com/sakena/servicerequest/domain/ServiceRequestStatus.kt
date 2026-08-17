package com.sakena.servicerequest.domain

enum class ServiceRequestStatus {
    PENDING,      // Waiting for manager review
    APPROVED,     // Manager approved
    ASSIGNED, // Manager assigned to worker
    IN_PROGRESS,  // Assigned to worker
    COMPLETED,    // Staff marked done, awaiting resident confirmation
    CONFIRMED,    // Resident confirmed the work — now payable
    SETTLED,      // Worker wage paid out from the building account
    REJECTED      // Manager rejected
}
