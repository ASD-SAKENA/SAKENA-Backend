package com.sakena.property.domain

import com.sakena.property.domain.model.BuildingId
import com.sakena.shared.domain.EntityNotFoundException

class BuildingNotFoundException(id: BuildingId) :
    EntityNotFoundException("Building with id '$id' was not found")
