package com.sakena.notification.infrastructure.web

import com.sakena.notification.application.NotificationService
import com.sakena.notification.domain.model.NotificationId
import com.sakena.notification.infrastructure.web.dto.NotificationResponse
import com.sakena.notification.infrastructure.web.dto.UnreadCountResponse
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications for the signed-in user")
@SecurityRequirement(name = "bearerAuth")
class NotificationController(
    private val notificationService: NotificationService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List your newest notifications")
    @GetMapping
    fun list(@RequestParam(required = false) limit: Int?): List<NotificationResponse> =
        notificationService.listFor(currentUser().id, limit ?: NotificationService.DEFAULT_LIMIT)
            .map(NotificationResponse::from)

    @Operation(summary = "Count of unread notifications")
    @GetMapping("/unread-count")
    fun unreadCount(): UnreadCountResponse =
        UnreadCountResponse(notificationService.unreadCount(currentUser().id))

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: String): NotificationResponse =
        NotificationResponse.from(
            notificationService.markRead(NotificationId.from(id), currentUser().id),
        )

    @Operation(summary = "Mark every notification as read")
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead() {
        notificationService.markAllRead(currentUser().id)
    }

    private fun currentUser(): User {
        val username = SecurityContextHolder.getContext().authentication.name
        return profileService.getUserByUsername(username)
            ?: throw IllegalStateException("Authenticated user '$username' no longer exists")
    }
}
