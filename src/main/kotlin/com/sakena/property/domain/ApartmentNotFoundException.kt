package com.sakena.property.domain

import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.EntityNotFoundException

class ApartmentNotFoundException(id: ApartmentId) :
    EntityNotFoundException("Apartment with id '$id' was not found")
