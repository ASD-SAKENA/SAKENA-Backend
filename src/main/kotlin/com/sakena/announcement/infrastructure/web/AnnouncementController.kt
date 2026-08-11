package com.sakena.announcement.infrastructure.web

import com.sakena.announcement.application.AnnouncementService
import com.sakena.announcement.infrastructure.web.dto.AnnouncementResponse
import com.sakena.announcement.infrastructure.web.dto.CreateAnnouncementRequest
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for the Announcement bounded context. Controllers stay thin:
 * parse/validate input, delegate to the application service, map the result
 * back to a DTO.
 */
@RestController
@RequestMapping("/api/v1/announcements")
@Tag(name = "Announcements", description = "Publish and read building announcements")
@SecurityRequirement(name = "bearerAuth")
class AnnouncementController(
    private val announcementService: AnnouncementService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List announcements, newest first")
    @GetMapping
    fun list(): List<AnnouncementResponse> =
        announcementService.getAll().map(AnnouncementResponse::from)

    @Operation(summary = "Publish a new announcement (manager)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateAnnouncementRequest): AnnouncementResponse {
        val announcement = announcementService.create(request.toCommand(), getCurrentUserId())
        return AnnouncementResponse.from(announcement)
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }
}
