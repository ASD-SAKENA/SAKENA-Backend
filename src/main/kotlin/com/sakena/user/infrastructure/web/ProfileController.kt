package com.sakena.user.infrastructure.web

import com.sakena.user.application.ChangePasswordCommand
import com.sakena.user.application.ProfileService
import com.sakena.user.application.UpdateProfileCommand
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "User profile management")
class ProfileController(
    private val profileService: ProfileService
) {

    @GetMapping
    @Operation(summary = "Get current user profile")
    fun getProfile(): ProfileResponse {
        val userId = getCurrentUserId()
        val user = profileService.getProfile(userId)
        return toResponse(user)
    }

    @PutMapping
    @Operation(summary = "Update profile (username or email)")
    fun updateProfile(@RequestBody @Valid request: UpdateProfileRequest): ProfileResponse {
        val userId = getCurrentUserId()
        val command = UpdateProfileCommand(request.username, request.email)
        val updated = profileService.updateProfile(userId, command)
        return toResponse(updated)
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password")
    fun changePassword(@RequestBody @Valid request: ChangePasswordRequest) {
        val userId = getCurrentUserId()
        val command = ChangePasswordCommand(request.currentPassword, request.newPassword)
        profileService.changePassword(userId, command)
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }

    private fun toResponse(user: User): ProfileResponse {
        return ProfileResponse(
            id = user.id.value.toString(),
            username = user.username,
            email = user.email,
            role = user.role,
            createdAt = user.createdAt,
            active = user.active
        )
    }
}
