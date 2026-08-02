package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainUnauthorizedException

class InvalidCredentialsException : DomainUnauthorizedException("Invalid username or password")
