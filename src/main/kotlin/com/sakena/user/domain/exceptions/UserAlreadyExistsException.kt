package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainException

class UserAlreadyExistsException(field: String, value: String)
    : DomainException("User with $field '$value' already exists")
