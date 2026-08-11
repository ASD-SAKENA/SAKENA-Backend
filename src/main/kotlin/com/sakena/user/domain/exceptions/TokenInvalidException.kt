package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainValidationException

class TokenInvalidException : DomainValidationException("Invalid or expired reset token")
