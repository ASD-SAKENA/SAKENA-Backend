package com.sakena.support.application.command

import com.sakena.support.domain.model.TicketCategory
import com.sakena.support.domain.model.TicketMessageKind

/** Opening a ticket always carries its first message. */
data class OpenTicketCommand(
    val category: TicketCategory,
    val subject: String,
    val body: String,
    val anonymous: Boolean,
)

/**
 * One reply in the thread — either text or a previously uploaded attachment,
 * never both, mirroring how a message is modelled.
 */
data class ReplyToTicketCommand(
    val body: String?,
    val kind: TicketMessageKind,
    val storageKey: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val durationSeconds: Int?,
)

/** Bytes handed to the storage port, kept free of Spring's MultipartFile. */
data class TicketAttachmentUpload(
    val originalFilename: String?,
    val contentType: String,
    val sizeBytes: Long,
    val content: java.io.InputStream,
)
