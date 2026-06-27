package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainException

class InvalidCredentialsException : DomainException("Invalid username or password")
