package com.sakena.user.application

data class ResetPasswordCommand(
    val token: String,
    val newPassword: String
)

