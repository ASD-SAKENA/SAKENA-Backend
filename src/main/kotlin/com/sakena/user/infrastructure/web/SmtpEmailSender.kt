package com.sakena.user.infrastructure.web

import com.sakena.user.application.EmailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
class SmtpEmailSender(
    private val mailSender: JavaMailSender
) : EmailSender {

    override fun sendPasswordResetEmail(to: String, resetLink: String) {
        val message = SimpleMailMessage().apply {
            setTo(to)
            setSubject("Password Reset Request")
            setText("""
                You requested a password reset.
                Click the link below to set a new password:
                $resetLink

                If you did not request this, please ignore this email.
            """.trimIndent())
        }
        mailSender.send(message)
    }
}
