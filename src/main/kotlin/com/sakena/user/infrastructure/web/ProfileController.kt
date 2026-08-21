package com.sakena.user.infrastructure.web

import com.sakena.shared.domain.AuthenticatedUserNotFoundException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.application.AvatarUpload
import com.sakena.user.application.ChangePasswordCommand
import com.sakena.user.application.ProfileService
import com.sakena.user.application.UpdateProfileCommand
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "User profile management")
@SecurityRequirement(name = "bearerAuth")
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

    @PostMapping("/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Set the current user's profile picture")
    fun setAvatar(@RequestPart("file") file: MultipartFile): ProfileResponse {
        if (file.isEmpty) throw DomainValidationException("The uploaded file is empty")
        val userId = getCurrentUserId()
        val updated = file.inputStream.use { stream ->
            profileService.setAvatar(
                userId,
                AvatarUpload(
                    originalFilename = file.originalFilename,
                    contentType = file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    sizeBytes = file.size,
                    content = stream,
                ),
            )
        }
        return toResponse(updated)
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Remove the profile picture and fall back to the initial")
    fun removeAvatar(): ProfileResponse =
        toResponse(profileService.removeAvatar(getCurrentUserId()))

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw AuthenticatedUserNotFoundException()
        return user.id
    }

    private fun toResponse(user: User): ProfileResponse {
        return ProfileResponse(
            id = user.id.value.toString(),
            username = user.username,
            email = user.email,
            role = user.role,
            createdAt = user.createdAt,
            active = user.active,
            managedBuildingId = user.managedBuildingId?.value,
            avatarUrl = profileService.avatarUrl(user),
        )
    }
}
