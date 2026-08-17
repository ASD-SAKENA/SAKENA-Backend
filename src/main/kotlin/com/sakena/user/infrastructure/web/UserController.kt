package com.sakena.user.infrastructure.web

import com.sakena.property.domain.model.BuildingId
import com.sakena.user.application.UserAdminService
import com.sakena.user.domain.Role
import com.sakena.user.domain.UserId
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

/**
 * System-wide user administration. Restricted to ADMIN — a building manager
 * only manages their own building's units and residents, not other people's
 * accounts across the whole system (see property/residency/membership for
 * the manager-facing equivalents, each scoped to the requester's building).
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "System-wide user administration (admin only)")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userAdminService: UserAdminService
) {

    @GetMapping
    @Operation(summary = "List all users, optionally filtered by role")
    fun list(@RequestParam(required = false) role: Role?): List<UserSummaryResponse> =
        userAdminService.getUsers(role).map(UserSummaryResponse::from)

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a user account")
    fun changeStatus(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateUserStatusRequest
    ): UserSummaryResponse =
        UserSummaryResponse.from(
            userAdminService.changeActiveStatus(UserId.fromString(id), request.active)
        )

    @PatchMapping("/{id}/specialty")
    @Operation(summary = "Set or clear a user's specialty")
    fun changeSpecialty(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateUserSpecialtyRequest
    ): UserSummaryResponse =
        UserSummaryResponse.from(
            userAdminService.changeSpecialty(UserId.fromString(id), request.specialty)
        )

    @PatchMapping("/{id}/role")
    @Operation(
        summary = "Change a user's role, including granting or revoking MANAGER of a building " +
            "(a building may have several managers, or none)",
    )
    fun changeRole(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateUserRoleRequest
    ): UserSummaryResponse =
        UserSummaryResponse.from(
            userAdminService.changeRole(
                UserId.fromString(id),
                Role.from(request.role),
                request.managedBuildingId?.let(BuildingId::from),
            ),
        )
}
