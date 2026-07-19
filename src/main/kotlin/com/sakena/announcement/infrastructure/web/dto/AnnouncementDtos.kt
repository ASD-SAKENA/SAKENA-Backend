package com.sakena.announcement.infrastructure.web.dto

import com.sakena.announcement.application.command.CreateAnnouncementCommand
import com.sakena.announcement.domain.model.Announcement
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateAnnouncementRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,

    @field:NotBlank(message = "body must not be blank")
    @field:Size(max = 4000, message = "body must be at most 4000 characters")
    val body: String,
) {
    fun toCommand() = CreateAnnouncementCommand(title = title, body = body)
}

data class AnnouncementResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val createdBy: UUID,
    val createdAt: Instant,
) {
    companion object {
        fun from(announcement: Announcement) = AnnouncementResponse(
            id = announcement.id.value,
            title = announcement.title,
            body = announcement.body,
            createdBy = announcement.createdBy.value,
            createdAt = announcement.createdAt,
        )
    }
}
