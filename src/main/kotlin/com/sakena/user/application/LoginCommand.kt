package com.sakena.user.application

import com.sakena.user.domain.User

data class LoginCommand(
    val username: String,
    val password: String
)

data class LoginResult(
    val token: String,
    val user: User,
)
