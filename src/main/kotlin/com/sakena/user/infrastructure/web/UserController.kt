package com.sakena.user.infrastructure.web

import com.sakena.user.application.UserAdminService
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@PreAuthorize("hasRole('MANAGER')")
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userAdminService: UserAdminService,
    private val profileService: ProfileService,
) {

    @GetMapping
    @Operation(summary = "List all users, optionally filtered by role")
    fun list(
        @RequestParam(required = false) role: Role?,
        principal: Principal,
    ): List<UserSummaryResponse> =
        userAdminService.getUsers(currentManagerId(principal), role).map(UserSummaryResponse::from)

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a user account")
    fun changeStatus(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateUserStatusRequest,
        principal: Principal,
    ): UserSummaryResponse =
        UserSummaryResponse.from(
            userAdminService.changeActiveStatus(
                UserId.fromString(id),
                request.active,
                currentManagerId(principal),
            ),
        )

    @PatchMapping("/{id}/specialty")
    @Operation(summary = "Set or clear a user's specialty")
    fun changeSpecialty(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateUserSpecialtyRequest,
        principal: Principal,
    ): UserSummaryResponse =
        UserSummaryResponse.from(
            userAdminService.changeSpecialty(
                UserId.fromString(id),
                request.specialty,
                currentManagerId(principal),
            ),
        )

    private fun currentManagerId(principal: Principal): UserId =
        profileService.getUserByUsername(principal.name)?.id
            ?: throw EntityNotFoundException("Signed-in manager was not found")
}
