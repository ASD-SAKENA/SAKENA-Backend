package com.sakena.announcement.domain.model

import com.sakena.shared.domain.DomainValidationException
import java.util.UUID

/**
 * Value object identifying an [Announcement] aggregate. Wrapping the raw [UUID]
 * keeps the type system honest — an `AnnouncementId` can never be mixed up with
 * some other id.
 */
@JvmInline
value class AnnouncementId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): AnnouncementId = AnnouncementId(UUID.randomUUID())

        fun from(raw: String): AnnouncementId =
            try {
                AnnouncementId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid announcement id")
            }
    }
}
