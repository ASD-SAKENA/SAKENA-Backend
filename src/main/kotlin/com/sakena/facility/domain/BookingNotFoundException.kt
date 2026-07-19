package com.sakena.facility.domain

import com.sakena.facility.domain.model.BookingId
import com.sakena.shared.domain.EntityNotFoundException

class BookingNotFoundException(id: BookingId) :
    EntityNotFoundException("Booking with id '$id' was not found")
