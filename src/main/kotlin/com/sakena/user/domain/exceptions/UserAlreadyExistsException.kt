package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainConflictException

class UserAlreadyExistsException(field: String, value: String)
    : DomainConflictException("User with $field '$value' already exists")
