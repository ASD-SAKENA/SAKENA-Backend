package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainValidationException

class InvalidRoleException(value: String) : DomainValidationException("Invalid role '$value'")
