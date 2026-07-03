package com.sakena.user.application

interface EmailSender {
    fun sendPasswordResetEmail(to: String, resetLink: String)
}
