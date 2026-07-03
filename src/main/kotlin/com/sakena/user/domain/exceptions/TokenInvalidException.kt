package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainException

class TokenInvalidException : DomainException("Invalid or expired reset token")
