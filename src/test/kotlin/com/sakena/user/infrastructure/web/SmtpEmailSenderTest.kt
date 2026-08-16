package com.sakena.user.infrastructure.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class SmtpEmailSenderTest {

    private val mailSender = mockk<JavaMailSender>()
    private val sender = SmtpEmailSender(mailSender)

    @Test
    fun `sends the reset link in the message body`() {
        val captured = slot<SimpleMailMessage>()
        every { mailSender.send(capture(captured)) } returns Unit

        sender.sendPasswordResetEmail("resident@sakena.test", "https://sakena.test/reset-password?token=abc")

        verify { mailSender.send(any<SimpleMailMessage>()) }
        assert(captured.captured.text?.contains("https://sakena.test/reset-password?token=abc") == true)
    }

    @Test
    fun `an SMTP failure does not propagate to the caller`() {
        every { mailSender.send(any<SimpleMailMessage>()) } throws
            MailAuthenticationException("535 5.7.8 Username and Password not accepted")

        // Must not throw - a delivery failure should never surface as a
        // request-level error (see the class doc for why).
        sender.sendPasswordResetEmail("resident@sakena.test", "https://sakena.test/reset-password?token=abc")

        verify { mailSender.send(any<SimpleMailMessage>()) }
    }
}
