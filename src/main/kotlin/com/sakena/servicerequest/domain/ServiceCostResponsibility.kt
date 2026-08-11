package com.sakena.servicerequest.domain

/** Identifies who ultimately bears a completed service request's cost. */
enum class ServiceCostResponsibility {
    ALL_UNITS,
    REQUESTING_UNIT,
    BUILDING_WALLET,
}
