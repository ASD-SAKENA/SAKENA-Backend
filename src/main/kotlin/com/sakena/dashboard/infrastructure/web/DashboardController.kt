package com.sakena.dashboard.infrastructure.web

import com.sakena.dashboard.application.DashboardService
import com.sakena.dashboard.infrastructure.web.dto.ManagerDashboardResponse
import com.sakena.dashboard.infrastructure.web.dto.ResidentDashboardResponse
import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

/**
 * Read-only projections powering the two home screens. Each endpoint answers
 * one screen in a single call, so the client never fans out across contexts.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregated home-screen figures for residents and managers")
@SecurityRequirement(name = "bearerAuth")
class DashboardController(
    private val dashboardService: DashboardService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "The signed-in resident's home screen")
    @GetMapping("/resident")
    fun resident(): ResidentDashboardResponse {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw IllegalStateException("Authenticated user '$username' no longer exists")
        return ResidentDashboardResponse.from(dashboardService.forResident(user.id))
    }

    @Operation(summary = "Building-wide figures for the manager home screen")
    @GetMapping("/manager")
    fun manager(principal: Principal): ManagerDashboardResponse =
        ManagerDashboardResponse.from(dashboardService.forManager(currentManagerId(principal)))

    private fun currentManagerId(principal: Principal): UserId =
        profileService.getUserByUsername(principal.name)?.id
            ?: throw EntityNotFoundException("Signed-in manager was not found")
}
