package com.sakena.user.infrastructure.web

import com.sakena.user.application.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * A delivery failure never invalidates the reset request — the token is
 * already committed by the time this runs, so the caller (forgotPassword)
 * still returns its generic "if this email exists" response either way,
 * consistent with EmailInvitationNotifier for the same reason: letting an
 * SMTP failure surface as a request-level error would both break the flow
 * for a transient outage and weakly reveal whether the account exists.
 */
@Component
class SmtpEmailSender(
    private val mailSender: JavaMailSender
) : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)

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
        runCatching { mailSender.send(message) }
            .onFailure { log.warn("Could not send password reset email to {}: {}", to, it.message) }
    }
}
