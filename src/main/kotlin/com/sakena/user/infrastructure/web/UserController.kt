package com.sakena.user.infrastructure.web

import com.sakena.user.application.UserAdminService
import com.sakena.user.domain.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userAdminService: UserAdminService
) {

    @GetMapping
    @Operation(summary = "List all users, optionally filtered by role")
    fun list(@RequestParam(required = false) role: Role?): List<UserSummaryResponse> =
        userAdminService.getUsers(role).map(UserSummaryResponse::from)
}
