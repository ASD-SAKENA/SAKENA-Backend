package com.sakena.facility.domain

import com.sakena.facility.domain.model.FacilityId
import com.sakena.shared.domain.EntityNotFoundException

class FacilityNotFoundException(id: FacilityId) :
    EntityNotFoundException("Facility with id '$id' was not found")
