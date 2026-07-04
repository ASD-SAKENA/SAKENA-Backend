package com.sakena.user.application

data class ChangePasswordCommand(
    val currentPassword: String,
    val newPassword: String
)
