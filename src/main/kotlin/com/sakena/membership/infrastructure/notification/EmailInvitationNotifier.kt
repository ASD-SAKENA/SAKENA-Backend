package com.sakena.membership.infrastructure.notification

import com.sakena.membership.domain.InvitationNotifier
import com.sakena.membership.domain.model.BuildingInvitation
import com.sakena.membership.domain.model.InvitationChannel
import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Delivers email invitations. Phone and open-link invitations are not sent
 * anywhere — the manager shares those links directly — and a delivery failure
 * never invalidates the invitation, so the link keeps working either way.
 */
@Component
class EmailInvitationNotifier(
    private val mailSender: JavaMailSender,
) : InvitationNotifier {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun notify(
        invitation: BuildingInvitation,
        buildingName: String,
        acceptUrl: String,
    ) {
        if (invitation.channel != InvitationChannel.EMAIL) return
        val recipient = invitation.recipient ?: return

        runCatching {
            mailSender.send(
                SimpleMailMessage().apply {
                    setTo(recipient)
                    subject = "دعوت به ساختمان $buildingName در ساکنا"
                    text = buildString {
                        appendLine("سلام،")
                        appendLine()
                        appendLine("شما به ساختمان «$buildingName» در سامانه ساکنا دعوت شده‌اید.")
                        appendLine("برای پیوستن روی لینک زیر کلیک کنید:")
                        appendLine(acceptUrl)
                        appendLine()
                        appendLine("این لینک تا ${BuildingInvitation.DEFAULT_VALIDITY_DAYS} روز معتبر است.")
                    }
                },
            )
        }.onFailure {
            log.warn("Could not email invitation {}: {}", invitation.id, it.message)
        }
    }
}
