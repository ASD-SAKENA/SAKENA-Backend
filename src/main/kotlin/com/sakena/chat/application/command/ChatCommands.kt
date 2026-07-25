package com.sakena.chat.application.command

import com.sakena.chat.domain.model.ChatMessageKind
import java.io.InputStream

data class SendTextMessageCommand(
    val body: String,
)

data class SendAttachmentCommand(
    val kind: ChatMessageKind,
    val originalFilename: String?,
    val contentType: String,
    val sizeBytes: Long,
    val content: InputStream,
    val caption: String?,
    /** Voice-note length in seconds; null for images. */
    val durationSeconds: Int?,
)

data class EditMessageCommand(
    val body: String,
)
