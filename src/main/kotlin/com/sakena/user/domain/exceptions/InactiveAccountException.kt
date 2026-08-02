package com.sakena.user.domain.exceptions

import com.sakena.shared.domain.DomainForbiddenException

class InactiveAccountException : DomainForbiddenException("User account is inactive")
